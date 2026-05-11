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
        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getStats().getTotalGameCount(),
                user.getStats().getWinGameCount(),
                user.isGuest());
    }
}
