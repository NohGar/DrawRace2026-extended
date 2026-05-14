package backend.drawrace.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.chat.entity.AiChatMessage;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
    List<AiChatMessage> findByType(AiChatMessage.MessageType type);
}
