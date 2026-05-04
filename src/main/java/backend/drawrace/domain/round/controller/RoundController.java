package backend.drawrace.domain.round.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.RoundSubmissionResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Round & Game API", description = "게임 시작 및 그림 제출")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundService roundService;

    @Operation(summary = "그림 제출", description = "그린 그림을 제출하고 AI 판별 점수(0.0~1.0)를 받습니다.")
    @PostMapping("/{roundId}/submit")
    public RsData<SubmitDrawingResponse> submitDrawing(
            @PathVariable Long roundId,
            @Valid @RequestBody SubmitDrawingRequest request,
            @AuthenticationPrincipal SecurityUser securityUser) {
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, securityUser.getUserId(), request);
        return new RsData<>("200-1", "그림 제출이 완료되었습니다.", response);
    }

    @Operation(summary = "라운드 제출 목록 조회", description = "종료된 라운드의 참가자별 그림, AI 판별 결과, 점수, 우승 여부를 조회합니다.")
    @GetMapping("/{roundId}/submissions")
    public RsData<List<RoundSubmissionResponse>> getRoundSubmissions(
            @PathVariable Long roundId, @AuthenticationPrincipal SecurityUser securityUser) {

        List<RoundSubmissionResponse> responses = roundService.getRoundSubmissions(roundId, securityUser.getUserId());

        return new RsData<>("200-1", "라운드 제출 목록 조회가 완료되었습니다.", responses);
    }
}
