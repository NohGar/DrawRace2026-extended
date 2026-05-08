package backend.drawrace.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.user.entity.User;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByRoomId(Long roomId);

    List<Participant> findByRoomIdAndIsLeftFalse(Long roomId);

    long countByRoomId(Long roomId);

    long countByRoomIdAndIsLeftFalse(Long roomId);

    Optional<Participant> findByIdAndRoomId(Long participantId, Long roomId);

    Optional<Participant> findByRoomAndUserId(Room room, User user);

    Optional<Participant> findByUserId(User user);

    Optional<Participant> findFirstByUserId_IdAndIsLeftFalseOrderByIdDesc(Long userId);

    boolean existsByRoomIdAndUserId_Id(Long roomId, Long userId);

    boolean existsByRoomIdAndUserId_IdAndIsLeftFalse(Long roomId, Long userId);

    Optional<Participant> findByRoomIdAndUserId_Id(Long roomId, Long userId);

    Optional<Participant> findByRoomIdAndUserId_IdAndIsLeftFalse(Long roomId, Long userId);

    @Query("""
        select count(p)
        from Participant p
        where p.room.id = :roomId
          and p.isLeft = false
          and p.userId.isAi = false
        """)
    long countActiveHumanByRoomId(@Param("roomId") Long roomId);

    @Query("""
        select p
        from Participant p
        join fetch p.userId u
        where p.room.id = :roomId
          and p.isLeft = false
        """)
    List<Participant> findActiveParticipantsByRoomId(@Param("roomId") Long roomId);
}
