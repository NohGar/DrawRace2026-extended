package backend.drawrace.domain.room.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import backend.drawrace.global.entity.BaseEntity;

import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    private String password;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "total_rounds", nullable = false)
    private short totalRounds;

    @Column(name = "max_players", nullable = false)
    private short maxPlayers;

    @Column(name = "cur_players", nullable = false)
    @Builder.Default
    private short curPlayers = 1; // 방 생성 시 방장은 자동으로 포함되므로 1부터 시작

    @Column(name = "is_playing", nullable = false)
    @Builder.Default
    private boolean isPlaying = false;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @Version // 동시성 제어를 위한 버전 관리
    private Long version;

    public void addParticipant(Participant participant) {
        this.participants.add(participant);
        this.curPlayers++;
    }

    /**
     * 게임 시작 전 대기방 퇴장 때만 사용합니다.
     * 게임 중에는 round_participant가 participant를 참조할 수 있으므로,
     * 이 메서드로 컬렉션에서 제거하면 orphanRemoval에 의해 삭제되어 FK 오류가 발생할 수 있습니다.
     */
    public void removeParticipant(Participant participant) {
        this.participants.remove(participant);
        decreaseCurPlayers();
    }

    /**
     * 게임 중/게임 후 퇴장 처리입니다.
     * participant를 삭제하지 않고 isLeft=true로만 변경합니다.
     */
    public void markParticipantLeft(Participant participant) {
        if (!participant.isLeft()) {
            participant.leave();
            decreaseCurPlayers();
        }
    }

    private void decreaseCurPlayers() {
        if (this.curPlayers > 0) {
            this.curPlayers--;
        }
    }

    public void changeHost(Long newHostId) {
        this.hostId = newHostId;
    }

    public void startGame() {
        this.isPlaying = true;
    }

    public void finishGame() {
        this.isPlaying = false;
    }
}