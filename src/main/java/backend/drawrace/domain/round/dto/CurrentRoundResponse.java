package backend.drawrace.domain.round.dto;

import java.time.LocalDateTime;
import java.util.List;

import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentRoundResponse {

    private Long roomId;
    private Long roundId;
    private int roundNumber;
    private String keyword;
    private RoundStatus status;
    private boolean isTiebreaker;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    /** 그리기 제한 시간(초). RoundService 의 ROUND_TIME_LIMIT 과 동일해야 한다. */
    private int timeLimit;
    private List<RoundParticipantResponse> participants;

    public static CurrentRoundResponse of(
            Round round, List<RoundParticipantResponse> participants, int timeLimitSeconds) {
        return CurrentRoundResponse.builder()
                .roomId(round.getRoom().getId())
                .roundId(round.getId())
                .roundNumber(round.getRoundNumber())
                .keyword(round.getKeyword())
                .status(round.getStatus())
                .isTiebreaker(round.isTiebreaker())
                .startedAt(round.getStartedAt())
                .endedAt(round.getEndedAt())
                .timeLimit(timeLimitSeconds)
                .participants(participants)
                .build();
    }
}
