package backend.drawrace.domain.round.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * AI 유저의 라운드 자동 제출을 담당한다.
 * ai.mode=quickdraw 일 때만 활성화된다.
 *
 * RoundService와 순환 참조가 발생하므로 @Lazy로 지연 주입한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
public class AiSubmissionService {

    // 실제 사람처럼 보이도록 랜덤 딜레이를 준다 (5~15초)
    private static final long AI_DELAY_MIN_MS = 5_000L;
    private static final long AI_DELAY_MAX_MS = 15_000L;

    private final AiDrawingService aiDrawingService;
    private final RoundService roundService;
    private final ObjectMapper objectMapper;

    public AiSubmissionService(
            AiDrawingService aiDrawingService, @Lazy RoundService roundService, ObjectMapper objectMapper) {
        this.aiDrawingService = aiDrawingService;
        this.roundService = roundService;
        this.objectMapper = objectMapper;
    }

    /**
     * 비동기로 AI 제출을 실행한다.
     * 딜레이 후 executeSubmission()을 호출한다.
     */
    @Async
    public void trigger(Long roundId, Long aiParticipantId, Long aiUserId, String keyword) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(AI_DELAY_MIN_MS, AI_DELAY_MAX_MS + 1);
            Thread.sleep(delay);
            executeSubmission(roundId, aiParticipantId, aiUserId, keyword);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 제출 중단. roundId={}", roundId);
        }
    }

    /**
     * 실제 제출 로직. 딜레이와 분리되어 있어 테스트에서 직접 호출 가능하다.
     * 실패 시 빈 데이터로 폴백 제출하여 라운드가 중단되지 않도록 한다.
     */
    void executeSubmission(Long roundId, Long aiParticipantId, Long aiUserId, String keyword) {
        try {
            DrawingData drawing = aiDrawingService.generateDrawing(keyword);
            String imageData = objectMapper.writeValueAsString(drawing.strokes());
            roundService.submitDrawing(roundId, aiUserId, new SubmitDrawingRequest(aiParticipantId, imageData));
        } catch (Exception e) {
            log.warn("AI 제출 실패. 폴백 제출 시도. roundId={}", roundId, e);
            submitFallback(roundId, aiParticipantId, aiUserId);
        }
    }

    private void submitFallback(Long roundId, Long aiParticipantId, Long aiUserId) {
        try {
            roundService.submitDrawing(roundId, aiUserId, new SubmitDrawingRequest(aiParticipantId, "[]"));
        } catch (Exception e) {
            log.error("AI 폴백 제출도 실패. 라운드가 중단될 수 있음. roundId={}", roundId, e);
        }
    }
}
