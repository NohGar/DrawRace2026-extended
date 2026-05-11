package backend.drawrace.domain.chat.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    public enum MessageType {
        TALK, // 일반 채팅
        NOTICE, // 시스템 알림 - 입장, 퇴장, 시작, 위임
        WINNER // 시스템 알림 - 우승자 공지
    }

    private MessageType type;
    private Long roomId;
    private String sender; // 보낸 사람 닉네임 (시스템 메시지는 "System")
    private String message;
}
