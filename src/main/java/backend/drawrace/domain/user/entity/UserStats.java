package backend.drawrace.domain.user.entity;

import jakarta.persistence.*;

import backend.drawrace.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStats extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private int totalGameCount = 0;
    private int winGameCount = 0;

    @Builder
    public UserStats(User user, int totalGameCount, int winGameCount) {
        this.user = user;
        this.totalGameCount = totalGameCount;
        this.winGameCount = winGameCount;
    }

    public void recordGame() {
        this.totalGameCount++;
    }

    public void recordWin() {
        this.winGameCount++;
    }
}
