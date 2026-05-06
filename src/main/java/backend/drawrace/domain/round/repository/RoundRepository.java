package backend.drawrace.domain.round.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.drawrace.domain.round.entity.Round;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findByRoomIdAndIsActiveTrue(Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Round r WHERE r.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);
}
