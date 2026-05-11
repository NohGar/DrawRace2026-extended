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

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

        User user = userRepository
                .findById(securityUser.getUserId())
                .orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        // AI 검열 실행
        String filteredMessage =
                chatModerationService.filterMessage(securityUser.getUserId(), chatMessage.getMessage());

        chatMessage.setSender(user.getNickname());
        chatMessage.setMessage(filteredMessage);
        // 일반 채팅은 TALK 타입으로 고정하여 브로드캐스팅
        chatMessage.setType(ChatMessageDto.MessageType.TALK);
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);
    }
}
