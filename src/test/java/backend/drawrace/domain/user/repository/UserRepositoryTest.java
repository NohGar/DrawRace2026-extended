package backend.drawrace.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import backend.drawrace.domain.user.entity.User;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이메일로_존재_여부_확인")
    void existsByEmail_success() {
        User user = User.builder()
                .email("jpa@test.com")
                .password("1234")
                .nickname("JPA테스터")
                .isAi(false)
                .build();
        userRepository.save(user);

        boolean exists = userRepository.existsByEmail("jpa@test.com");
        boolean notExists = userRepository.existsByEmail("noname@test.com");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("회원_저장_및_조회")
    void save_and_find() {
        User user = User.builder()
                .email("save@test.com")
                .password("1234")
                .nickname("저장테스트")
                .isAi(false)
                .build();

        User savedUser = userRepository.save(user);
        User findUser = userRepository.findById(savedUser.getId()).orElse(null);

        assertThat(findUser).isNotNull();
        assertThat(findUser.getEmail()).isEqualTo("save@test.com");
        assertThat(findUser.getNickname()).isEqualTo("저장테스트");
    }
}
