package backend.drawrace.domain.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ChatModerationServiceTest {

    @Mock
    private AiProperties aiProperties;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Spy
    @InjectMocks
    private ChatModerationService chatModerationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /*
       @Test
       @DisplayName("AI가 비속어를 발견하면 해당 단어만 ****로 치환된 문장을 반환한다")
       void shouldReturnFilteredMessage() {
           String input = "야 이 바보야";
           String expected = "야 이 ****야";

           doReturn(expected).when(chatModerationService).getAiDecision(input);

           String result = chatModerationService.filterMessage(1L, input);

           assertThat(result).isEqualTo(expected);
       }

       @Test
       @DisplayName("AI 서버 에러 발생 시 원문이 그대로 반환된다")
       void shouldReturnOriginalOnFailure() {
           // AI 서버 통신 중 예외 발생 시나리오
           String input = "테스트";
           doThrow(new RuntimeException("API 서버 다운")).when(chatModerationService).getAiDecision(input);

           String result = chatModerationService.filterMessage(1L, input);

           assertThat(result).isEqualTo("테스트");
       }

    */

    @Test
    @DisplayName("3자 미만의 짧은 문장(ㅎㅇ, ㅇㅇ)은 임베딩 검사를 건너뛰고 통과한다")
    void shouldPassShortMessagesWithoutEmbedding() {
        Long userId = 1L;
        String shortMessage = "띠용"; // 3자 미만

        String result = chatModerationService.fastFilter(userId, shortMessage);

        assertThat(result).isEqualTo(shortMessage);
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("첫 번째 적발 시 스트라이크가 1 증가하고 24시간 제한이 걸린다")
    void shouldIncreaseStrikeAndSetExpiryOnFirstOffense() {
        Long userId = 1L;
        String strikeKey = "strike:user:" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(strikeKey)).thenReturn(1L); // 첫 번째 적발 시뮬레이션

        ReflectionTestUtils.invokeMethod(chatModerationService, "applyStrikeAndCheckBan", userId);

        verify(valueOperations).increment(strikeKey);
        verify(redisTemplate).expire(eq(strikeKey), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("스트라이크가 3회 누적되면 3일간 차단되고 스트라이크 기록이 삭제된다")
    void shouldBanUserFor3DaysOnThirdOffense() {
        Long userId = 1L;
        String strikeKey = "strike:user:" + userId;
        String banKey = "ban:user:" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(strikeKey)).thenReturn(3L); // 3회 적발 시뮬레이션

        ReflectionTestUtils.invokeMethod(chatModerationService, "applyStrikeAndCheckBan", userId);

        verify(valueOperations).set(eq(banKey), eq("BANNED"), eq(3L), eq(TimeUnit.DAYS));
        verify(redisTemplate).delete(strikeKey);
    }

    @Test
    @DisplayName("차단된 유저가 채팅을 시도하면 403 예외가 발생한다")
    void fastFilter_ShouldThrowException_WhenUserIsBanned() {
        Long userId = 1L;
        String message = "안녕하세요";
        when(redisTemplate.hasKey("ban:user:" + userId)).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class, () -> {
            chatModerationService.fastFilter(userId, message);
        });

        assertEquals("403-3", exception.getResultCode());
        assertTrue(exception.getMessage().contains("채팅이 금지되었습니다"));
    }

    @Test
    @DisplayName("임베딩 유사도가 높으면 부적절한 메시지로 판정한다")
    void shouldFilterByEmbedding() {

        String input = "나쁜 문장";
        float[] mockVector = new float[] {0.1f, 0.2f};

        when(embeddingService.embed(anyString())).thenReturn(mockVector);

        chatModerationService.initAggressiveVectors();

        when(embeddingService.calculateSimilarity(any(), any())).thenReturn(0.95);

        String result = chatModerationService.fastFilter(1L, input);

        assertThat(result).isEqualTo("[부적절한 메시지입니다]");
    }

    @Test
    @DisplayName("비속어 패턴에 걸리면 즉시 ****로 치환한다")
    void shouldFilterByPattern() {
        String input = "야 이 시발";
        String result = chatModerationService.fastFilter(1L, input);
        assertThat(result).contains("****");
    }

    @Test
    @DisplayName("1초 이내에 연속으로 채팅을 보내면 도배 에러가 발생한다")
    void shouldThrowExceptionWhenSpamming() {
        Long userId = 1L;
        String input = "안녕하세요";

        when(embeddingService.embed(anyString())).thenReturn(new float[] {0.0f});
        // doReturn(input).when(chatModerationService).getAiDecision(input);
        chatModerationService.fastFilter(userId, input);

        assertThatThrownBy(() -> chatModerationService.fastFilter(userId, input))
                .isInstanceOf(backend.drawrace.global.exception.ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "429-1");
    }
}
