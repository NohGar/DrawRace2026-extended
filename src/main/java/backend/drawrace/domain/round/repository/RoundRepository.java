package backend.drawrace.domain.round.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.drawrace.domain.round.entity.Round;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findByRoomIdAndIsActiveTrue(Long roomId);

    /**
     * 그림 제출 저장·집계 직전에 호출한다. 동시에 제출할 때 submittedCount 레이스를 막기 위한 배타 락.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Round r join fetch r.room where r.id = :id")
    Optional<Round> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Round r WHERE r.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);
}
