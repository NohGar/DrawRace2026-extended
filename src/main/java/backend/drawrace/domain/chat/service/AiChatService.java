package backend.drawrace.domain.chat.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
@RequiredArgsConstructor
public class AiChatService {

    private static final long CHAT_DELAY_MIN_MS = 2_000L;
    private static final long CHAT_DELAY_MAX_MS = 5_000L;

    private final AiChatMessagePoolService messagePoolService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    public void triggerOnAiJoin(Long roomId, String aiNickname) {
        try {
            sleep();
            broadcast(roomId, aiNickname, messagePoolService.pop(MessageType.JOIN));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 채팅 실패 (입장). roomId={}", roomId, e);
        }
    }

    @Async
    public void triggerOnAiSubmit(Long roomId, String aiNickname) {
        try {
            sleep();
            broadcast(roomId, aiNickname, messagePoolService.pop(MessageType.SUBMIT));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 채팅 실패 (제출). roomId={}", roomId, e);
        }
    }

    private void sleep() throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(CHAT_DELAY_MIN_MS, CHAT_DELAY_MAX_MS + 1);
        Thread.sleep(delay);
    }

    private void broadcast(Long roomId, String aiNickname, String message) {
        if (message == null || message.isBlank()) return;
        ChatMessageDto chatMessage = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TALK)
                .roomId(roomId)
                .sender(aiNickname)
                .message(message)
                .build();
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);
    }
}
