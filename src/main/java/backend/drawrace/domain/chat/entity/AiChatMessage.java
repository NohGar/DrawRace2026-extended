package backend.drawrace.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_chat_message_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(nullable = false)
    private String message;

    public enum MessageType {
        JOIN,
        SUBMIT
    }

    public static AiChatMessage of(MessageType type, String message) {
        AiChatMessage entity = new AiChatMessage();
        entity.type = type;
        entity.message = message;
        return entity;
    }
}
