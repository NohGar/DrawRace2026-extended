package backend.drawrace.domain.user.dto;

import backend.drawrace.domain.user.entity.User;

public record FriendInfoResponse(Long id, String nickname, String profileImageUrl) {
    public static FriendInfoResponse from(User friend) {
        return new FriendInfoResponse(friend.getId(), friend.getNickname(), friend.getProfileImageUrl());
    }
}
