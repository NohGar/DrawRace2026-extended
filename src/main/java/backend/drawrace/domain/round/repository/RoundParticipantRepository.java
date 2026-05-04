package backend.drawrace.domain.round.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.drawrace.domain.round.entity.RoundParticipant;

public interface RoundParticipantRepository extends JpaRepository<RoundParticipant, Long> {

    boolean existsByRoundIdAndParticipantId(Long roundId, Long participantId);

    long countByRoundId(Long roundId);

    @Query("""
        select count(rp)
        from RoundParticipant rp
        where rp.round.id = :roundId
          and rp.participant.isLeft = false
        """)
    long countActiveByRoundId(@Param("roundId") Long roundId);

    List<RoundParticipant> findByRoundId(Long roundId);

    @Query("""
        select rp
        from RoundParticipant rp
        join fetch rp.participant p
        join fetch p.userId u
        where rp.round.id = :roundId
          and p.isLeft = false
        """)
    List<RoundParticipant> findActiveByRoundId(@Param("roundId") Long roundId);
}
