package backend.drawrace.domain.round.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiInferenceResponse {

    private String aiAnswer;
    private double score;
}
