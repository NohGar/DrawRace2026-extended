package backend.drawrace.domain.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import backend.drawrace.domain.chat.controller.ChatController;
import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.global.security.SecurityUser;

@SpringBootTest
class ChatPerformanceTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatPerformanceTest.class);

    @Autowired
    private ChatController chatController;

    @Test
    @DisplayName("실제 로직을 돌려서 콘솔에 찍히는 시간을 확인한다")
    void checkChatSpeed() {
        // 1. 가짜 데이터 준비 (채은이가 테스트하고 싶은 문장)
        Long roomId = 1L;
        ChatMessageDto message = ChatMessageDto.builder()
                .message("그림 진짜 못 그리네") // 유사도 검사가 일어날 문장
                .build();

        // 2. Authentication 객체 만들기 (Security 환경이라면 필요해)
        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(securityUser, null, null);

        // 3. 컨트롤러 메서드 직접 호출!
        log.info("=== 테스트 시작 ===");
        chatController.sendChatMessage(roomId, message, auth);
        log.info("=== 테스트 종료 ===");
    }
}
