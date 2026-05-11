package backend.drawrace.domain.user.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import lombok.Getter;

@Getter
@RedisHash(value = "refreshToken", timeToLive = 604800)
public class RefreshToken {

    @Id
    private Long userId;

    private String tokenValue;

    public RefreshToken(Long userId, String tokenValue) {
        this.userId = userId;
        this.tokenValue = tokenValue;
    }
}
