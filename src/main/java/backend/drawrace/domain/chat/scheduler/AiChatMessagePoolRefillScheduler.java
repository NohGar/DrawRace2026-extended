package backend.drawrace.domain.chat.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.drawrace.domain.chat.service.AiChatMessagePoolService;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
@RequiredArgsConstructor
public class AiChatMessagePoolRefillScheduler {

    private final AiChatMessagePoolService aiChatMessagePoolService;

    // 앱 시작 직후 및 5분마다 풀 보충
    @Scheduled(initialDelay = 0, fixedDelay = 5 * 60 * 1000)
    public void refill() {
        aiChatMessagePoolService.refill();
    }
}
