package backend.drawrace.domain.user.dto;

import backend.drawrace.domain.user.entity.User;

public record UserInfoResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        int totalGameCount,
        int winGameCount,
        boolean isGuest) {
    public static UserInfoResponse from(User user) {
        return of(user, user.getProfileImageUrl());
    }

    public static UserInfoResponse of(User user, String profileImageUrl) {
        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                profileImageUrl,
                user.getStats().getTotalGameCount(),
                user.getStats().getWinGameCount(),
                user.isGuest());
    }
}
