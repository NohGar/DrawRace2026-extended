package backend.drawrace.domain.round.dto.gateway;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GatewayInferenceResult {

    private String aiAnswer;
    private double score;
}
