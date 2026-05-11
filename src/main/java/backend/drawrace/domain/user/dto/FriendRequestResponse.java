package backend.drawrace.domain.user.dto;

import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.User;

public record FriendRequestResponse(Long friendshipId, Long userId, String nickname, String profileImageUrl) {
    public static FriendRequestResponse from(Friendship friendship, User otherUser) {
        return new FriendRequestResponse(
                friendship.getId(), otherUser.getId(), otherUser.getNickname(), otherUser.getProfileImageUrl());
    }
}
