package backend.drawrace.domain.round.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.security.SecurityUser;

@WebMvcTest(RoundController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoundService roundService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("그림 제출 요청 성공")
    void submitDrawing_success() throws Exception {
        Long roundId = 10L;
        Long userId = 1L;

        SecurityUser securityUser = new SecurityUser(userId, "test@test.com");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SubmitDrawingRequest request = new SubmitDrawingRequest();
        setField(request, "participantId", 100L);
        setField(request, "imageData", "dummy-image");

        SubmitDrawingResponse response = SubmitDrawingResponse.builder()
                .roundId(roundId)
                .submittedAiAnswer("사과")
                .submittedScore(0.95)
                .submittedCount(2)
                .totalParticipantCount(2)
                .roundFinished(true)
                .gameFinished(false)
                .tieBreakerStarted(false)
                .roundWinnerParticipantId(100L)
                .roundWinnerAiAnswer("사과")
                .roundWinnerScore(0.95)
                .nextRoundId(20L)
                .nextRoundNumber(2)
                .nextRoundTieBreaker(false)
                .finalWinnerParticipantId(null)
                .build();

        given(roundService.submitDrawing(eq(roundId), eq(userId), any(SubmitDrawingRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/rounds/{roundId}/submit", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("그림 제출이 완료되었습니다."))
                .andExpect(jsonPath("$.data.roundId").value(10))
                .andExpect(jsonPath("$.data.submittedAiAnswer").value("사과"))
                .andExpect(jsonPath("$.data.submittedScore").value(0.95))
                .andExpect(jsonPath("$.data.submittedCount").value(2))
                .andExpect(jsonPath("$.data.totalParticipantCount").value(2))
                .andExpect(jsonPath("$.data.roundFinished").value(true))
                .andExpect(jsonPath("$.data.gameFinished").value(false))
                .andExpect(jsonPath("$.data.tieBreakerStarted").value(false))
                .andExpect(jsonPath("$.data.roundWinnerParticipantId").value(100))
                .andExpect(jsonPath("$.data.roundWinnerAiAnswer").value("사과"))
                .andExpect(jsonPath("$.data.roundWinnerScore").value(0.95))
                .andExpect(jsonPath("$.data.nextRoundId").value(20))
                .andExpect(jsonPath("$.data.nextRoundNumber").value(2))
                .andExpect(jsonPath("$.data.nextRoundTieBreaker").value(false));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
