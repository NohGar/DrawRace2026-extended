package backend.drawrace.domain.round.service;

import backend.drawrace.domain.round.dto.AiInferenceResponse;

public interface AiInferenceService {
    AiInferenceResponse infer(String imageData, String keyword);
}
