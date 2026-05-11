package backend.drawrace.domain.room.dto.response;

import lombok.Builder;

@Builder
public record GameEvent(
        String type, // ROUND_START, ROUND_END, GAME_OVER 등
        Object data // 이벤트와 관련된 추가 정보
        ) {}
