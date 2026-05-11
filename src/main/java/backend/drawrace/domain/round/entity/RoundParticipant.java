package backend.drawrace.domain.round.entity;

import jakarta.persistence.*;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoundParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "round_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Builder
    private RoundParticipant(Round round, Participant participant) {
        this.round = round;
        this.participant = participant;
    }

    public static RoundParticipant of(Round round, Participant participant) {
        return RoundParticipant.builder().round(round).participant(participant).build();
    }
}
