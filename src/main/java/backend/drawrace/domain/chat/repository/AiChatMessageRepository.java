package backend.drawrace.domain.chat.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import backend.drawrace.domain.chat.entity.AiChatMessage;
import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiChatMessage> findFirstByType(MessageType type);

    long countByType(MessageType type);
}
