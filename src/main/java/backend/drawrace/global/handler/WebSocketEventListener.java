package backend.drawrace.global.handler;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import backend.drawrace.domain.room.dto.response.RoomUpdateResponse;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // [인증] StompHandler에서 저장한 유저 정보 추출
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken token) {
            SecurityUser user = (SecurityUser) token.getPrincipal();
            Long userId = user.getUserId();

            log.info("웹소켓 연결 종료 감지: 유저 ID {}", userId);

            // [실시간 동기화]
            // 1. disconnect 이벤트에는 roomId가 없을 수 있으므로, userId로 현재 active 참가 정보를 찾음
            // 2. RoomService에서 DB 처리 및 방장 위임 로직을 수행하고 RoomUpdateResponse를 받아옴
            RoomUpdateResponse updateInfo = roomService.leaveCurrentRoom(userId);

            if (updateInfo != null) {
                // 3. 방에 남은 참여자들에게 바뀐 상태 실시간 전송
                messagingTemplate.convertAndSend("/sub/rooms/" + updateInfo.getRoomId(), updateInfo);
            }
        }
    }
}