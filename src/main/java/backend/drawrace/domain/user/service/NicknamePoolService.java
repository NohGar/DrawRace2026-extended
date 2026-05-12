package backend.drawrace.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.entity.NicknamePool;
import backend.drawrace.domain.user.repository.NicknamePoolRepository;
import backend.drawrace.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NicknamePoolService {

    private static final int POOL_SIZE = 30;
    private static final int MAX_RETRIES = 5;

    private final NicknamePoolRepository nicknamePoolRepository;
    private final UserRepository userRepository;
    private final GuestNicknameGenerator nicknameGenerator;

    @Transactional
    public String pop() {
        return nicknamePoolRepository
                .findFirstBy()
                .map(entry -> {
                    nicknamePoolRepository.delete(entry);
                    return entry.getNickname();
                })
                .orElse(null);
    }

    @Transactional
    public void refill() {
        long current = nicknamePoolRepository.count();
        int needed = (int) (POOL_SIZE - current);
        if (needed <= 0) return;

        log.info("[NicknamePool] 현재 {}개, {}개 보충 시작", current, needed);
        int filled = 0;
        for (int i = 0; i < needed; i++) {
            if (tryAddOne()) filled++;
        }
        log.info("[NicknamePool] {}개 보충 완료", filled);
    }

    private boolean tryAddOne() {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            String nickname = nicknameGenerator.generate();
            if (!nicknamePoolRepository.existsByNickname(nickname) && !userRepository.existsByNickname(nickname)) {
                nicknamePoolRepository.save(
                        NicknamePool.builder().nickname(nickname).build());
                return true;
            }
        }
        log.warn("[NicknamePool] {}회 재시도 후 닉네임 생성 실패, 건너뜀", MAX_RETRIES);
        return false;
    }
}
