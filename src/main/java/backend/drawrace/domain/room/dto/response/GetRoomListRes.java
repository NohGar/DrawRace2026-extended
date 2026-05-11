package backend.drawrace.domain.room.dto.response;

import lombok.Builder;

@Builder
public record GetRoomListRes(
        Long roomId, String title, short curPlayers, short maxPlayers, boolean isPlaying, String hostNickname) {}
