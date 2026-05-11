package backend.drawrace.domain.round.entity;

import jakarta.persistence.*;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoundSubmission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "round_submission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Lob
    @Column(nullable = false)
    private String imageData;

    @Column(nullable = false)
    private String aiAnswer;

    @Column(nullable = false)
    private double score;

    private RoundSubmission(Round round, Participant participant, String imageData, String aiAnswer, double score) {
        this.round = round;
        this.participant = participant;
        this.imageData = imageData;
        this.aiAnswer = aiAnswer;
        this.score = score;
    }

    public static RoundSubmission create(
            Round round, Participant participant, String imageData, String aiAnswer, double score) {
        return new RoundSubmission(round, participant, imageData, aiAnswer, score);
    }
}
