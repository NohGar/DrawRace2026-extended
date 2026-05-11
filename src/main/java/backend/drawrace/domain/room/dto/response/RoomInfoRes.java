package backend.drawrace.domain.room.dto.response;

import java.util.List;

public record RoomInfoRes(
        Long roomId,
        String title,
        short curPlayers,
        short maxPlayers,
        short totalRounds,
        Long hostId,
        boolean isPlaying,
        List<ParticipantDto> participants) {
    public record ParticipantDto(Long userId, String nickname, boolean isHost, boolean isAi) {}
}
