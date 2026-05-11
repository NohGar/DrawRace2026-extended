package backend.drawrace.global.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ParticipantRepository participantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // CONNECT 프레임일 때(처음 연결할 때) 토큰 검증
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 토큰 유효성 검사 및 인증 정보 설정
            if (jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                accessor.setUser(authentication);
            } else {
                log.error("웹소켓 인증 실패: 유효하지 않은 토큰");
                throw new RuntimeException("인증 정보가 유효하지 않습니다.");
            }
        }

        // 구독 시 권한 체크
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination(); // 예: /sub/rooms/10/chat
            Long roomId = extractRoomId(destination);

            if (roomId != null) {
                // 현재 인증된 유저 정보 가져오기
                Authentication authentication = (Authentication) accessor.getUser();
                if (authentication == null) {
                    throw new ServiceException("401-1", "인증 정보가 없습니다.");
                }

                SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
                Long userId = securityUser.getUserId();

                // DB에서 해당 유저가 이 방의 참여자인지 확인
                boolean isParticipant = participantRepository.existsByRoomIdAndUserId_Id(roomId, userId);

                if (!isParticipant) {
                    log.warn("인가되지 않은 구독 시도: 유저 {}, 방 {}", userId, roomId);
                    throw new ServiceException("403-1", "해당 방에 접근 권한이 없습니다.");
                }
            }
        }
        return message;
    }

    private Long extractRoomId(String destination) {
        try {
            if (destination == null || !destination.startsWith("/sub/rooms/")) return null;

            // /sub/rooms/{roomId} 형식에서 숫자 추출
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                return Long.parseLong(parts[3]);
            }
        } catch (Exception e) {
            log.error("roomId 추출 실패: {}", destination);
        }
        return null;
    }
}
