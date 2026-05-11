package backend.drawrace.domain.user.dto;

import backend.drawrace.domain.user.entity.User;

public record UserSearchResponse(Long id, String nickname, String profileImageUrl) {
    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
