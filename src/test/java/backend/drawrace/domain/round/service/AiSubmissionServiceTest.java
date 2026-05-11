package backend.drawrace.domain.round.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;

@ExtendWith(MockitoExtension.class)
class AiSubmissionServiceTest {

    @Mock
    private AiDrawingService aiDrawingService;

    @Mock
    private RoundService roundService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiSubmissionService aiSubmissionService;

    @Test
    @DisplayName("executeSubmission 호출 시 그림을 생성하고 submitDrawing을 호출한다")
    void executeSubmission_callsSubmitDrawing() throws Exception {
        Long roundId = 1L;
        Long aiParticipantId = 100L;
        Long aiUserId = 200L;
        String keyword = "사과";

        DrawingData drawing = new DrawingData(List.of(List.of(List.of(0, 1), List.of(2, 3))));
        given(aiDrawingService.generateDrawing(keyword)).willReturn(drawing);
        given(objectMapper.writeValueAsString(drawing.strokes())).willReturn("[[[0,1],[2,3]]]");

        aiSubmissionService.executeSubmission(roundId, aiParticipantId, aiUserId, keyword);

        then(aiDrawingService).should().generateDrawing(keyword);
        then(roundService)
                .should()
                .submitDrawing(
                        eq(roundId),
                        eq(aiUserId),
                        argThat(req -> req.getParticipantId().equals(aiParticipantId)
                                && req.getImageData().equals("[[[0,1],[2,3]]]")));
    }

    @Test
    @DisplayName("그림 생성 실패 시 빈 데이터로 폴백 제출한다")
    void executeSubmission_whenDrawingFails_submitsFallback() throws Exception {
        Long roundId = 1L;
        Long aiParticipantId = 100L;
        Long aiUserId = 200L;
        String keyword = "사과";

        given(aiDrawingService.generateDrawing(keyword)).willThrow(new RuntimeException("그림 생성 실패"));

        aiSubmissionService.executeSubmission(roundId, aiParticipantId, aiUserId, keyword);

        // 폴백으로 빈 데이터 제출 시도
        then(roundService)
                .should()
                .submitDrawing(
                        eq(roundId),
                        eq(aiUserId),
                        argThat(req -> req.getParticipantId().equals(aiParticipantId)
                                && req.getImageData().equals("[]")));
    }

    @Test
    @DisplayName("폴백 제출도 실패하면 예외가 전파되지 않는다")
    void executeSubmission_whenFallbackFails_doesNotThrow() throws Exception {
        Long roundId = 1L;
        Long aiParticipantId = 100L;
        Long aiUserId = 200L;
        String keyword = "사과";

        given(aiDrawingService.generateDrawing(keyword)).willThrow(new RuntimeException("그림 생성 실패"));
        willThrow(new RuntimeException("폴백 실패"))
                .given(roundService)
                .submitDrawing(any(), any(), any(SubmitDrawingRequest.class));

        // 예외가 외부로 전파되지 않아야 한다
        aiSubmissionService.executeSubmission(roundId, aiParticipantId, aiUserId, keyword);
    }
}
