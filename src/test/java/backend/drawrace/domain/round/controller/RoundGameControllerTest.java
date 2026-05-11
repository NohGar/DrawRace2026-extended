package backend.drawrace.domain.round.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

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

import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundParticipantResponse;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.security.SecurityUser;

@WebMvcTest(RoundGameController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoundGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoundService roundService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("게임 시작 요청 성공")
    void startGame_success() throws Exception {
        Long roomId = 1L;
        Long userId = 1L;

        SecurityUser securityUser = new SecurityUser(userId, "test@test.com");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RoundStartResponse response = RoundStartResponse.builder()
                .roomId(roomId)
                .roundId(10L)
                .roundNumber(1)
                .keyword("사과")
                .status(RoundStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.of(2026, 4, 21, 12, 0, 0))
                .build();

        given(roundService.startGame(roomId, userId)).willReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/start", roomId).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("게임이 시작되었습니다."))
                .andExpect(jsonPath("$.data.roomId").value(1))
                .andExpect(jsonPath("$.data.roundId").value(10))
                .andExpect(jsonPath("$.data.roundNumber").value(1))
                .andExpect(jsonPath("$.data.keyword").value("사과"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.startedAt").exists());
    }

    @Test
    @DisplayName("현재 라운드 조회 요청 성공")
    void getCurrentRound_success() throws Exception {
        Long roomId = 1L;
        Long userId = 1L;

        SecurityUser securityUser = new SecurityUser(userId, "test@test.com");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentRoundResponse response = CurrentRoundResponse.builder()
                .roomId(roomId)
                .roundId(10L)
                .roundNumber(2)
                .keyword("사과")
                .status(RoundStatus.IN_PROGRESS)
                .isTiebreaker(false)
                .startedAt(LocalDateTime.of(2026, 4, 21, 12, 0, 0))
                .participants(List.of(
                        RoundParticipantResponse.builder()
                                .participantId(100L)
                                .roundWinCount(1)
                                .isHost(true)
                                .isWinner(false)
                                .build(),
                        RoundParticipantResponse.builder()
                                .participantId(101L)
                                .roundWinCount(0)
                                .isHost(false)
                                .isWinner(false)
                                .build()))
                .build();

        given(roundService.getCurrentRound(roomId, userId)).willReturn(response);

        mockMvc.perform(get("/api/rooms/{roomId}/rounds/current", roomId).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-2"))
                .andExpect(jsonPath("$.msg").value("현재 라운드 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.roomId").value(1))
                .andExpect(jsonPath("$.data.roundId").value(10))
                .andExpect(jsonPath("$.data.roundNumber").value(2))
                .andExpect(jsonPath("$.data.keyword").value("사과"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.tiebreaker").value(false))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].participantId").value(100))
                .andExpect(jsonPath("$.data.participants[0].roundWinCount").value(1))
                .andExpect(jsonPath("$.data.participants[0].host").value(true))
                .andExpect(jsonPath("$.data.participants[0].winner").value(false));
    }
}
