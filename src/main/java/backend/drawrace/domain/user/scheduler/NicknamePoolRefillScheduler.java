package backend.drawrace.domain.user.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.drawrace.domain.user.service.NicknamePoolService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NicknamePoolRefillScheduler {

    private final NicknamePoolService nicknamePoolService;

    // 앱 시작 직후 및 5분마다 풀 보충
    @Scheduled(initialDelay = 0, fixedDelay = 5 * 60 * 1000)
    public void refill() {
        nicknamePoolService.refill();
    }
}
