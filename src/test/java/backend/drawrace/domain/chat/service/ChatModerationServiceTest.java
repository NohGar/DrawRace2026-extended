package backend.drawrace.domain.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import backend.drawrace.global.config.AiProperties;

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
    @DisplayName("임베딩 유사도가 높으면 부적절한 메시지로 판정한다")
    void shouldFilterByEmbedding() {

        String input = "나쁜 문장";
        float[] mockVector = new float[] {0.1f, 0.2f};

        when(embeddingService.embed(anyString())).thenReturn(mockVector);

        chatModerationService.initAggressiveVectors();

        when(embeddingService.calculateSimilarity(any(), any())).thenReturn(0.9);

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
