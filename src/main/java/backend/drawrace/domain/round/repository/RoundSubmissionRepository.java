package backend.drawrace.domain.round.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.round.entity.RoundSubmission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoundSubmissionRepository extends JpaRepository<RoundSubmission, Long> {

    boolean existsByRoundIdAndParticipantId(Long roundId, Long participantId);

    long countByRoundId(Long roundId);

    List<RoundSubmission> findByRoundId(Long roundId);

    @Query("""
        select rs
        from RoundSubmission rs
        join fetch rs.participant p
        join fetch p.userId u
        where rs.round.id = :roundId
        order by rs.score desc, rs.createdAt asc
        """)
    List<RoundSubmission> findAllWithParticipantAndUserByRoundIdOrderByScoreDescCreatedAtAsc(
            @Param("roundId") Long roundId);
}
