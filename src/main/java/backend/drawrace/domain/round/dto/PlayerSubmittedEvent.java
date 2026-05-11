package backend.drawrace.domain.round.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerSubmittedEvent {

    @Builder.Default
    private String type = "PLAYER_SUBMITTED";

    private Long roundId;
    private Long participantId;
    private int submittedCount;
    private int totalParticipantCount;
}
