package backend.drawrace.domain.chat.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.chat.service.ChatModerationService;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ChatModerationService chatModerationService;

    @MessageMapping("/rooms/{roomId}/chat")
    public void sendChatMessage(
            @DestinationVariable Long roomId, ChatMessageDto chatMessage, Authentication authentication) {

        // 빈 메시지나 공백만 있는 메시지
        if (chatMessage.getMessage() == null || chatMessage.getMessage().trim().isEmpty()) {
            return; // 아무것도 하지 않고 종료
        }

        org.springframework.util.StopWatch stopWatch = new org.springframework.util.StopWatch();
        stopWatch.start();

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

        User user = userRepository
                .findById(securityUser.getUserId())
                .orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        // AI 검열 실행
        String filteredMessage = chatModerationService.fastFilter(securityUser.getUserId(), chatMessage.getMessage());

        stopWatch.stop();
        log.info("[성능체크] 채팅 필터링 완료 - 소요시간: {}ms, 메시지: {}", stopWatch.getTotalTimeMillis(), filteredMessage);

        chatMessage.setSender(user.getNickname());
        chatMessage.setMessage(filteredMessage);
        // 일반 채팅은 TALK 타입으로 고정하여 브로드캐스팅
        chatMessage.setType(ChatMessageDto.MessageType.TALK);
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);

        chatModerationService.processAiModeration(roomId, chatMessage);
    }
}
