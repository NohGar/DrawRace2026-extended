package backend.drawrace.domain.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.chat.entity.AiChatMessage;
import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;
import backend.drawrace.domain.chat.repository.AiChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatMessagePoolService {

    private static final int POOL_SIZE = 20;

    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiChatMessageGenerator messageGenerator;

    @Transactional
    public String pop(MessageType type) {
        return aiChatMessageRepository
                .findFirstByType(type)
                .map(entry -> {
                    aiChatMessageRepository.delete(entry);
                    return entry.getMessage();
                })
                .orElse(null);
    }

    @Transactional
    public void refill() {
        for (MessageType type : MessageType.values()) {
            refill(type);
        }
    }

    private void refill(MessageType type) {
        long current = aiChatMessageRepository.countByType(type);
        int needed = (int) (POOL_SIZE - current);
        if (needed <= 0) return;

        log.info("[AiChatMessagePool] {} 타입 현재 {}개, {}개 보충 시작", type, current, needed);
        for (int i = 0; i < needed; i++) {
            String message = messageGenerator.generate(type);
            aiChatMessageRepository.save(AiChatMessage.of(type, message));
        }
        log.info("[AiChatMessagePool] {} 타입 {}개 보충 완료", type, needed);
    }
}
