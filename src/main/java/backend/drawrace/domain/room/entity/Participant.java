package backend.drawrace.domain.room.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.global.entity.BaseEntity;

import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "round_win_count", nullable = false)
    @Builder.Default
    private int roundWinCount = 0;

    @Column(name = "is_winner", nullable = false)
    @Builder.Default
    private boolean isWinner = false;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "is_left", nullable = false)
    @Builder.Default
    private boolean isLeft = false;

    // 유저가 삭제될 때 제출한 그림 기록도 함께 삭제
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoundSubmission> submissions = new ArrayList<>();

    // 유저가 삭제될 때 라운드 참여 정보도 함께 삭제
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoundParticipant> roundParticipants = new ArrayList<>();

    public void increaseRoundWinCount() {
        this.roundWinCount++;
    }

    public void markWinner() {
        this.isWinner = true;
    }

    public void makeHost() {
        this.isHost = true;
    }

    public void leave() {
        this.isLeft = true;
        this.isHost = false;
    }

    public void resetGameResult() {
        this.roundWinCount = 0;
        this.isWinner = false;
    }
}
