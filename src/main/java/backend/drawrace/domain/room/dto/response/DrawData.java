package backend.drawrace.domain.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "드로잉 좌표 데이터")
@Builder
public record DrawData(
        @Schema(description = "X 좌표", example = "10.5") double x,

        @Schema(description = "Y 좌표", example = "25.2") double y,

        @Schema(description = "상태 (START, MOVE, END)", example = "MOVE")
        String type, // START (눌렀을 때), MOVE (그릴 때), END (뗐을 때)

        @Schema(description = "색상 코드", example = "#FF0000") String color,

        @Schema(description = "펜 굵기", example = "5") int penSize) {}
