package backend.drawrace.domain.room.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRoomReq(
        @NotBlank(message = "방 제목을 입력해 주세요.") String title,

        @Min(2) @Max(4) short maxPlayers,

        @Min(value = 1, message = "최소 1라운드 이상 설정해야 합니다.") @Max(value = 10, message = "최대 10라운드까지 설정 가능합니다.") short totalRounds,

        String password) {}
