package backend.drawrace.domain.round.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.entity.RoundStatus;

@ExtendWith(MockitoExtension.class)
class RoundEventListenerTest {

    @InjectMocks
    private RoundEventListener roundEventListener;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("게임 시작 이벤트 수신 시 참가자에게 제시어가 포함된 라운드 정보를 전송한다")
    void handleGameStarted_sendsRoundStartResponseWithKeyword() {
        Long roomId = 1L;
        RoundStartResponse roundStartResponse = RoundStartResponse.builder()
                .roomId(roomId)
                .roundId(10L)
                .roundNumber(1)
                .keyword("사과")
                .status(RoundStatus.IN_PROGRESS)
                .build();

        GameStartedEvent event = new GameStartedEvent(roomId, roundStartResponse);

        roundEventListener.handleGameStarted(event);

        ArgumentCaptor<RoundStartResponse> responseCaptor = ArgumentCaptor.forClass(RoundStartResponse.class);
        then(messagingTemplate).should().convertAndSend(eq("/sub/rooms/" + roomId), responseCaptor.capture());
        assertThat(responseCaptor.getValue().getKeyword()).isEqualTo("사과");
        assertThat(responseCaptor.getValue().getRoundId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("게임 시작 이벤트 수신 시 채팅창에 게임 시작 공지를 전송한다")
    void handleGameStarted_sendsStartNoticeToChat() {
        Long roomId = 1L;
        RoundStartResponse roundStartResponse = RoundStartResponse.builder()
                .roomId(roomId)
                .roundId(10L)
                .roundNumber(1)
                .keyword("사과")
                .status(RoundStatus.IN_PROGRESS)
                .build();

        GameStartedEvent event = new GameStartedEvent(roomId, roundStartResponse);

        roundEventListener.handleGameStarted(event);

        ArgumentCaptor<ChatMessageDto> chatCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        then(messagingTemplate).should().convertAndSend(eq("/sub/rooms/" + roomId + "/chat"), chatCaptor.capture());
        assertThat(chatCaptor.getValue().getType()).isEqualTo(ChatMessageDto.MessageType.NOTICE);
        assertThat(chatCaptor.getValue().getMessage()).contains("게임이 시작되었습니다");
    }
}
