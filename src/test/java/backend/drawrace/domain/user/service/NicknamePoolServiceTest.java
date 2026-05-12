package backend.drawrace.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.entity.NicknamePool;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.NicknamePoolRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.domain.user.scheduler.NicknamePoolRefillScheduler;

@SpringBootTest
@Transactional
class NicknamePoolServiceTest {

    @Autowired
    NicknamePoolService nicknamePoolService;

    @Autowired
    NicknamePoolRepository nicknamePoolRepository;

    @Autowired
    UserRepository userRepository;

    @MockBean
    GuestNicknameGenerator nicknameGenerator;

    @MockBean
    NicknamePoolRefillScheduler nicknamePoolRefillScheduler;

    private void saveToPool(String nickname) {
        nicknamePoolRepository.save(NicknamePool.builder().nickname(nickname).build());
    }

    private void createUserWithNickname(String nickname) {
        userRepository.save(User.builder()
                .email(nickname + "@test.com")
                .password("pw")
                .nickname(nickname)
                .isAi(false)
                .isGuest(false)
                .build());
    }

    // ===== pop() =====

    @Test
    @DisplayName("pop_풀에_닉네임_있을_때_반환_및_DB_삭제")
    void pop_returns_and_removes_nickname_when_pool_has_entry() {
        saveToPool("테스트닉네임");

        String result = nicknamePoolService.pop();

        assertThat(result).isEqualTo("테스트닉네임");
        assertThat(nicknamePoolRepository.count()).isZero();
    }

    @Test
    @DisplayName("pop_풀이_비어있을_때_null_반환")
    void pop_returns_null_when_pool_is_empty() {
        String result = nicknamePoolService.pop();

        assertThat(result).isNull();
    }

    // ===== refill() =====

    @Test
    @DisplayName("refill_풀이_비어있을_때_30개_채움")
    void refill_fills_pool_to_30_when_empty() {
        AtomicInteger counter = new AtomicInteger(0);
        given(nicknameGenerator.generate()).willAnswer(inv -> "닉네임" + counter.incrementAndGet());

        nicknamePoolService.refill();

        assertThat(nicknamePoolRepository.count()).isEqualTo(30);
    }

    @Test
    @DisplayName("refill_부족한_만큼만_채움")
    void refill_fills_only_missing_count() {
        for (int i = 1; i <= 25; i++) {
            saveToPool("기존닉네임" + i);
        }
        AtomicInteger counter = new AtomicInteger(0);
        given(nicknameGenerator.generate()).willAnswer(inv -> "신규닉네임" + counter.incrementAndGet());

        nicknamePoolService.refill();

        assertThat(nicknamePoolRepository.count()).isEqualTo(30);
    }

    @Test
    @DisplayName("refill_이미_30개_이상이면_생성하지_않음")
    void refill_does_not_overfill_when_pool_already_full() {
        for (int i = 1; i <= 30; i++) {
            saveToPool("닉네임" + i);
        }

        nicknamePoolService.refill();

        assertThat(nicknamePoolRepository.count()).isEqualTo(30);
        verify(nicknameGenerator, never()).generate();
    }

    @Test
    @DisplayName("refill_users_테이블_중복_닉네임은_건너뜀")
    void refill_skips_nickname_already_taken_by_user() {
        for (int i = 1; i <= 29; i++) {
            saveToPool("닉네임" + i);
        }
        createUserWithNickname("중복닉네임");
        given(nicknameGenerator.generate()).willReturn("중복닉네임", "신규닉네임");

        nicknamePoolService.refill();

        assertThat(nicknamePoolRepository.count()).isEqualTo(30);
        assertThat(nicknamePoolRepository.existsByNickname("중복닉네임")).isFalse();
        assertThat(nicknamePoolRepository.existsByNickname("신규닉네임")).isTrue();
    }

    @Test
    @DisplayName("refill_nickname_pool_중복_닉네임은_건너뜀")
    void refill_skips_nickname_already_in_pool() {
        for (int i = 1; i <= 29; i++) {
            saveToPool("닉네임" + i);
        }
        given(nicknameGenerator.generate()).willReturn("닉네임1", "신규닉네임");

        nicknamePoolService.refill();

        assertThat(nicknamePoolRepository.count()).isEqualTo(30);
        assertThat(nicknamePoolRepository.existsByNickname("신규닉네임")).isTrue();
    }
}