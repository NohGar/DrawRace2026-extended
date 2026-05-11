package backend.drawrace.domain.round.service;

import backend.drawrace.domain.round.dto.DrawingData;

public interface AiDrawingService {
    DrawingData generateDrawing(String keyword);
}
