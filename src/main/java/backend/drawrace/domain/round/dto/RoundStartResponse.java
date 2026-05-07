package backend.drawrace.domain.round.dto;

import java.time.LocalDateTime;

import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoundStartResponse {

    private Long roomId;
    private Long roundId;
    private int roundNumber;
    private String keyword;
    private RoundStatus status;
    private LocalDateTime startedAt;
    private int timeLimit;

    public static RoundStartResponse from(Round round, int timeLimit) {
        return RoundStartResponse.builder()
                .roomId(round.getRoom().getId())
                .roundId(round.getId())
                .roundNumber(round.getRoundNumber())
                .keyword(round.getKeyword())
                .status(round.getStatus())
                .startedAt(round.getStartedAt())
                .timeLimit(timeLimit)
                .build();
    }
}
