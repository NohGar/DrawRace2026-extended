package backend.drawrace.domain.round.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "round_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private String keyword;

    @Column(nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(name = "is_tiebreaker", nullable = false)
    private boolean isTiebreaker;

    private Round(Room room, int roundNumber, String keyword, boolean isTiebreaker) {
        this.room = room;
        this.roundNumber = roundNumber;
        this.keyword = keyword;
        this.status = RoundStatus.READY;
        this.isActive = false;
        this.isTiebreaker = isTiebreaker;
    }

    public static Round create(Room room, int roundNumber, String keyword) {
        return new Round(room, roundNumber, keyword, false);
    }

    public static Round createTieBreaker(Room room, int roundNumber, String keyword) {
        return new Round(room, roundNumber, keyword, true);
    }

    public void start() {
        this.status = RoundStatus.IN_PROGRESS;
        this.isActive = true;
        this.startedAt = LocalDateTime.now();
    }

    public void finish() {
        this.status = RoundStatus.FINISHED;
        this.isActive = false;
        this.endedAt = LocalDateTime.now();
    }
}
