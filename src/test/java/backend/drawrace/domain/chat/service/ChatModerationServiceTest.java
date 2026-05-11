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

import backend.drawrace.global.config.AiProperties;

@ExtendWith(MockitoExtension.class)
class ChatModerationServiceTest {

    @Mock
    private AiProperties aiProperties;

    @Spy
    @InjectMocks
    private ChatModerationService chatModerationService;

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

    @Test
    @DisplayName("1초 이내에 연속으로 채팅을 보내면 도배 에러가 발생한다")
    void shouldThrowExceptionWhenSpamming() {
        Long userId = 1L;
        String input = "안녕하세요";

        doReturn(input).when(chatModerationService).getAiDecision(input);
        chatModerationService.filterMessage(userId, input);

        assertThatThrownBy(() -> chatModerationService.filterMessage(userId, input))
                .isInstanceOf(backend.drawrace.global.exception.ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "429-1");
    }
}
