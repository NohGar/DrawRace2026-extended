package backend.drawrace.domain.room.dto.response;

import lombok.Builder;

@Builder
public record RankingRes(Long userId, String nickname, int roundWinCount, boolean isWinner) {}
