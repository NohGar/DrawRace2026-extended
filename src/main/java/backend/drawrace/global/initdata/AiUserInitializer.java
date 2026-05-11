package backend.drawrace.global.initdata;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail("ai@drawrace.com")) {
            User aiUser = User.builder()
                    .email("ai@drawrace.com")
                    .password(passwordEncoder.encode("AI_NO_LOGIN"))
                    .nickname("AI봇")
                    .isAi(true)
                    .build();
            userRepository.save(aiUser);
        }
    }
}
