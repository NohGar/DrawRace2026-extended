package backend.drawrace.domain.round.dto;

import backend.drawrace.domain.room.entity.Participant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoundParticipantResponse {

    private Long participantId;
    private int roundWinCount;
    private boolean isHost;
    private boolean isWinner;
    private boolean submitted;

    public static RoundParticipantResponse from(Participant participant) {
        return from(participant, false);
    }

    public static RoundParticipantResponse from(Participant participant, boolean submitted) {
        return RoundParticipantResponse.builder()
                .participantId(participant.getId())
                .roundWinCount(participant.getRoundWinCount())
                .isHost(participant.isHost())
                .isWinner(participant.isWinner())
                .submitted(submitted)
                .build();
    }
}
