package backend.drawrace.domain.user.entity;

import jakarta.persistence.*;

import backend.drawrace.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "nickname_pool")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NicknamePool extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nickname;

    @Builder
    public NicknamePool(String nickname) {
        this.nickname = nickname;
    }
}
