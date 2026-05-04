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

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public RsData<Long> signup(@RequestBody @Valid CreateUserRequest request) {
        Long userId = authService.signup(request);
        return new RsData<>("201-1", "회원가입이 완료되었습니다.", userId);
    }

    @PostMapping("/login")
    public RsData<LoginResponse> login(@RequestBody @Valid LoginRequest dto) {
        LoginResponse token = authService.login(dto);
        return new RsData<>("200-1", "로그인에 성공했습니다.", token);
    }

    @PostMapping("/reissue")
    public RsData<LoginResponse> reissue(@RequestBody @Valid TokenRequest dto) {
        LoginResponse token = authService.reissue(dto);
        return new RsData<>("200-2", "토큰이 재발급되었습니다.", token);
    }

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

    @PatchMapping("/password")
    public RsData<Void> updatePassword(
            @AuthenticationPrincipal SecurityUser user, @RequestBody @Valid UpdatePasswordRequest request) {
        authService.updatePassword(user.getUserId(), request);
        return new RsData<>("200-4", "비밀번호가 변경되었습니다.");
    }
}
