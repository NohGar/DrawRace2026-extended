package backend.drawrace.global.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import backend.drawrace.domain.room.dto.response.RoomUpdateResponse;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.global.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("세션 종료 이벤트 발생 시 RoomService의 leaveCurrentRoom이 호출된다")
    void handleDisconnect_ShouldInvokeLeaveCurrentRoom() {
        // given
        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(securityUser, null);

        RoomUpdateResponse response = RoomUpdateResponse.builder()
                .roomId(10L)
                .type("USER_LEAVE")
                .leaverId(1L)
                .message("유저A님이 퇴장하셨습니다.")
                .build();

        given(roomService.leaveCurrentRoom(1L)).willReturn(response);

        // STOMP 헤더 설정
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setUser(auth);
        accessor.setSessionId("session-123");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        // when
        webSocketEventListener.handleWebSocketDisconnectListener(event);

        // then
        then(roomService).should(times(1)).leaveCurrentRoom(1L);
        then(messagingTemplate).should(times(1)).convertAndSend(eq("/sub/rooms/10"), same(response));
    }

    @Test
    @DisplayName("퇴장 처리 결과가 null이면 방 상태를 전송하지 않는다")
    void handleDisconnect_ShouldNotSendMessage_WhenResponseIsNull() {
        // given
        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(securityUser, null);

        given(roomService.leaveCurrentRoom(1L)).willReturn(null);

        // STOMP 헤더 설정
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setUser(auth);
        accessor.setSessionId("session-123");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        // when
        webSocketEventListener.handleWebSocketDisconnectListener(event);

        // then
        then(roomService).should(times(1)).leaveCurrentRoom(1L);
        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
    }
}
