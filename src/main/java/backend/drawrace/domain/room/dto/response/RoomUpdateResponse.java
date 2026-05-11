package backend.drawrace.domain.room.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomUpdateResponse {
    private Long roomId;
    private String type; // "USER_LEAVE" 또는 "HOST_CHANGED"
    private Long leaverId; // 나간 유저 ID
    private Long newHostId; // 새 방장 ID (방장이 안 바뀌었으면 기존 방장 ID)
    private List<String> participants; // 현재 방에 남은 유저 닉네임 리스트
    private String message; // "OOO님이 퇴장하셨습니다."
}
