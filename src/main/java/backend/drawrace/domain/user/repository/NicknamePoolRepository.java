package backend.drawrace.domain.user.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import backend.drawrace.domain.user.entity.NicknamePool;

public interface NicknamePoolRepository extends JpaRepository<NicknamePool, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NicknamePool> findFirstBy();

    boolean existsByNickname(String nickname);
}
