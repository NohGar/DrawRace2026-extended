package backend.drawrace.domain.room.service;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomService roomService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // StompHandler에서 저장했던 인증 정보
        Authentication authentication = (Authentication) headerAccessor.getUser();

        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            Long userId = securityUser.getUserId();
            log.info("🔌 비정상 종료 감지: 유저 ID = {}", userId);

            try {
                roomService.leaveCurrentRoom(userId);
                log.info("✅ 유령 방 방지 - 자동 퇴장 처리 완료: 유저 ID = {}", userId);
            } catch (Exception e) {
                log.error("❌ 자동 퇴장 처리 중 오류 발생: {}", e.getMessage());
            }
        }
    }
}
