package backend.drawrace.domain.round.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import backend.drawrace.domain.chat.dto.ChatMessageDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoundEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGameStarted(GameStartedEvent event) {
        Long roomId = event.getRoomId();

        ChatMessageDto startNotice = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.NOTICE)
                .roomId(roomId)
                .sender("System")
                .message("게임이 시작되었습니다! 주제에 맞춰 그림을 그려주세요.")
                .build();

        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", startNotice);
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId, event.getRoundStartResponse());
    }
}
