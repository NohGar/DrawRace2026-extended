package backend.drawrace.domain.round.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import backend.drawrace.domain.round.dto.AiInferenceResponse;

@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockAiInferenceService implements AiInferenceService {

    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        return new AiInferenceResponse("사과", 0.9);
    }
}
