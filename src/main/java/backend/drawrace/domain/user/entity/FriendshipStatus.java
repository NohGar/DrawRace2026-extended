package backend.drawrace.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FriendshipStatus {
    PENDING("대기"),
    ACCEPTED("수락");

    private final String description;
}
