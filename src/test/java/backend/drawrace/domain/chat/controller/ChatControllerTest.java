package backend.drawrace.domain.chat.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.chat.service.ChatModerationService;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @InjectMocks
    private ChatController chatController;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatModerationService chatModerationService;

    @Test
    @DisplayName("사용자가 채팅을 보내면 TALK 타입으로 브로드캐스팅된다")
    void shouldSendChatMessageAsTalkType() {
        Long roomId = 1L;
        Long userId = 1L;
        String realNickname = "진짜닉네임";

        ChatMessageDto requestDto = ChatMessageDto.builder()
                .roomId(roomId)
                .sender("방장")
                .message("안녕하세요!")
                .build();

        SecurityUser securityUser = new SecurityUser(userId, "test@test.com");
        Authentication auth =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());

        User user = User.builder().nickname(realNickname).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(chatModerationService.filterMessage(anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        chatController.sendChatMessage(roomId, requestDto, auth);

        // 정확한 경로(/sub/rooms/{roomId}/chat)로 메시지가 가는지 확인
        // 메시지 타입이 TALK로 설정되었는지 확인
        verify(messagingTemplate, times(1))
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.TALK
                                && dto.getSender().equals(realNickname)
                                && dto.getMessage().equals("안녕하세요!")));
    }

    @Test
    @DisplayName("빈 메시지나 공백만 있는 메시지를 보내면 브로드캐스팅되지 않는다")
    void shouldNotSendEmptyMessage() {
        Long roomId = 1L;
        ChatMessageDto emptyRequest = ChatMessageDto.builder()
                .roomId(roomId)
                .message("   ") // 공백 메시지
                .build();

        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(securityUser, null, null);

        chatController.sendChatMessage(roomId, emptyRequest, auth);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("채팅 전송 시 AI 검열 서비스를 거쳐서 메시지가 나간다")
    void shouldCallModerationService() {
        Long roomId = 1L;
        String raw = "욕설";
        String filtered = "⚠️ 클린한 채팅 문화를 만들어주세요!";

        ChatMessageDto requestDto = ChatMessageDto.builder().message(raw).build();

        User user = User.builder().nickname("유저A").build();
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(chatModerationService.filterMessage(anyLong(), eq(raw))).thenReturn(filtered); // 가짜 응답

        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        Authentication auth = new UsernamePasswordAuthenticationToken(securityUser, null, null);

        // When
        chatController.sendChatMessage(roomId, requestDto, auth);

        // Then: 최종적으로 나가는 메시지가 '필터링된 문구'인지 확인!
        verify(messagingTemplate)
                .convertAndSend(eq("/sub/rooms/" + roomId + "/chat"), argThat((ChatMessageDto dto) -> dto.getMessage()
                        .equals(filtered)));
    }
}
