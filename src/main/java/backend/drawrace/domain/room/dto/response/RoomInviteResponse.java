package backend.drawrace.domain.room.dto.response;

public record RoomInviteResponse(
        Long roomId,
        String roomTitle,
        Long hostId,
        String hostNickname
) {}