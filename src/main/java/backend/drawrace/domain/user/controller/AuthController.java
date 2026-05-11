package backend.drawrace.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.LoginRequest;
import backend.drawrace.domain.user.dto.LoginResponse;
import backend.drawrace.domain.user.dto.TokenRequest;
import backend.drawrace.domain.user.dto.UpdatePasswordRequest;
import backend.drawrace.domain.user.service.AuthService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth API", description = "회원가입, 로그인 및 토큰 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 가입합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "가입 성공"),
        @ApiResponse(responseCode = "409", description = "이미 존재하는 이메일(409-1) 또는 닉네임(409-2)")
    })
    @PostMapping("/signup")
    public RsData<Long> signup(@RequestBody @Valid CreateUserRequest request) {
        Long userId = authService.signup(request);
        return new RsData<>("201-1", "회원가입이 완료되었습니다.", userId);
    }

    @Operation(summary = "로그인", description = "JWT Access/Refresh 토큰을 발급합니다.")
    @PostMapping("/login")
    public RsData<LoginResponse> login(@RequestBody @Valid LoginRequest dto) {
        LoginResponse token = authService.login(dto);
        return new RsData<>("200-1", "로그인에 성공했습니다.", token);
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/reissue")
    public RsData<LoginResponse> reissue(@RequestBody @Valid TokenRequest dto) {
        LoginResponse token = authService.reissue(dto);
        return new RsData<>("200-2", "토큰이 재발급되었습니다.", token);
    }

    @Operation(summary = "로그아웃")
    @DeleteMapping("/logout")
    public RsData<Void> logout(@AuthenticationPrincipal SecurityUser user) {
        authService.logout(user.getUserId());
        return new RsData<>("200-3", "로그아웃되었습니다.");
    }

    @PostMapping("/guest")
    public RsData<LoginResponse> guestLogin() {
        LoginResponse token = authService.guestLogin();
        return new RsData<>("201-2", "게스트 로그인에 성공했습니다.", token);
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 교체합니다.")
    @PatchMapping("/password")
    public RsData<Void> updatePassword(
            @AuthenticationPrincipal SecurityUser user, @RequestBody @Valid UpdatePasswordRequest request) {
        authService.updatePassword(user.getUserId(), request);
        return new RsData<>("200-4", "비밀번호가 변경되었습니다.");
    }
}
