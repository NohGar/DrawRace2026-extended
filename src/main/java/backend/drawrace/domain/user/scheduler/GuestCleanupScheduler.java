package backend.drawrace.domain.user.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(GuestCleanupScheduler.class);

    private final UserRepository userRepository;

    // 매일 자정 실행, 생성된 지 30일이 지난 게스트 계정 삭제
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void deleteExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<User> expiredGuests = userRepository.findAllByIsGuestTrueAndCreatedAtBefore(cutoff);

        userRepository.deleteAll(expiredGuests);
        log.info("[GuestCleanup] 만료된 게스트 계정 {}개 삭제 완료", expiredGuests.size());
    }
}
