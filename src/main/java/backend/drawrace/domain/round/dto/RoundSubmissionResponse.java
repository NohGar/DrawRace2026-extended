package backend.drawrace.domain.round.dto;

import backend.drawrace.domain.round.entity.RoundSubmission;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoundSubmissionResponse {

    private Long participantId;
    private String nickname;
    private String imageData;
    private String aiAnswer;
    private double score;
    private boolean winner;

    public static RoundSubmissionResponse from(RoundSubmission submission, boolean winner) {
        return RoundSubmissionResponse.builder()
                .participantId(submission.getParticipant().getId())
                .nickname(submission.getParticipant().getUserId().getNickname())
                .imageData(submission.getImageData())
                .aiAnswer(submission.getAiAnswer())
                .score(submission.getScore())
                .winner(winner)
                .build();
    }
}