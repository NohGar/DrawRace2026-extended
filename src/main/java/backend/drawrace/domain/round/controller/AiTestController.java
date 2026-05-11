package backend.drawrace.domain.round.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.domain.round.service.AiDrawingService;

import lombok.RequiredArgsConstructor;

// TODO: 테스트용 임시 컨트롤러, 삭제 예정
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTestController {

    private final AiDrawingService aiDrawingService;

    @GetMapping("/test")
    public DrawingData test(@RequestParam String keyword) {
        return aiDrawingService.generateDrawing(keyword);
    }
}
