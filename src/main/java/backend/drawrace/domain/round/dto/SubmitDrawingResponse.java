package backend.drawrace.domain.round.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitDrawingResponse {

    private Long roundId;

    // 제출한 사람의 AI 판별 결과
    private String submittedAiAnswer;
    private double submittedScore;

    private int submittedCount;
    private int totalParticipantCount;

    private boolean roundFinished;
    private boolean gameFinished;
    private boolean tieBreakerStarted;

    private Long roundWinnerParticipantId;

    // 라운드 승자의 AI 판별 결과
    private String roundWinnerAiAnswer;
    private Double roundWinnerScore;

    private Long nextRoundId;
    private Integer nextRoundNumber;
    private boolean nextRoundTieBreaker;

    private Long finalWinnerParticipantId;
}
