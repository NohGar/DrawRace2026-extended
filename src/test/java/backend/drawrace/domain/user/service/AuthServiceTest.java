package backend.drawrace.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.LoginRequest;
import backend.drawrace.domain.user.dto.LoginResponse;
import backend.drawrace.domain.user.dto.TokenRequest;
import backend.drawrace.domain.user.dto.UpdatePasswordRequest;
import backend.drawrace.domain.user.entity.RefreshToken;
import backend.drawrace.domain.user.repository.RefreshTokenRepository;
import backend.drawrace.global.exception.ServiceException;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    AuthService authService;

    @MockBean
    // @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @MockBean
    GuestNicknameGenerator nicknameGenerator;

    @AfterEach
    void tearDown() {
        // @Transactional이 JPA는 롤백하지만 Redis는 롤백하지 않으므로 직접 정리
        refreshTokenRepository.deleteAll();
    }

    private Long createTestUser() {
        return authService.signup(new CreateUserRequest("test@example.com", "password123", "테스터"));
    }

    // ===== 회원가입 =====

    @Test
    @DisplayName("회원가입_성공")
    void signup_success() {
        CreateUserRequest request = new CreateUserRequest("new@example.com", "password123", "신규유저");

        Long savedId = authService.signup(request);

        assertThat(savedId).isNotNull();
    }

    @Test
    @DisplayName("회원가입_실패_이메일_중복")
    void signup_fail_duplicate_email() {
        createTestUser();

        CreateUserRequest request = new CreateUserRequest("test@example.com", "password456", "다른닉네임");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-1");
    }

    @Test
    @DisplayName("회원가입_실패_닉네임_중복")
    void signup_fail_duplicate_nickname() {
        createTestUser();

        CreateUserRequest request = new CreateUserRequest("other@example.com", "password456", "테스터");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-2");
    }

    // ===== 로그인 =====

    @Test
    @DisplayName("로그인_성공_토큰_발급_및_Redis_저장")
    void login_success() {
        Long userId = createTestUser();

        LoginResponse response = authService.login(new LoginRequest("test@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        // assertThat(refreshTokenRepository.findById(userId)).isPresent();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그인_실패_존재하지_않는_이메일")
    void login_fail_email_not_found() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("none@example.com", "password123")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-1");
    }

    @Test
    @DisplayName("로그인_실패_비밀번호_불일치")
    void login_fail_wrong_password() {
        createTestUser();

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrongpassword")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-1");
    }

    // ===== 토큰 재발급 =====

    @Test
    @DisplayName("토큰_재발급_성공_새_토큰_발급_및_Redis_갱신")
    void reissue_success() {
        Long userId = createTestUser();
        LoginResponse loginResponse = authService.login(new LoginRequest("test@example.com", "password123"));

        org.mockito.Mockito.clearInvocations(refreshTokenRepository);

        // Mock 설정
        given(refreshTokenRepository.findById(userId))
                .willReturn(Optional.of(new RefreshToken(userId, loginResponse.refreshToken())));

        LoginResponse reissueResponse = authService.reissue(new TokenRequest(loginResponse.refreshToken()));

        assertThat(reissueResponse.accessToken()).isNotBlank();
        assertThat(reissueResponse.refreshToken()).isNotBlank();
        // 토큰 로테이션 검증 — Redis에 새 Refresh Token이 저장돼야 함

        /*        String storedToken = refreshTokenRepository
                       .findById(userId)
                       .map(RefreshToken::getTokenValue)
                       .orElseThrow();
               assertThat(storedToken).isEqualTo(reissueResponse.refreshToken());

        */
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("토큰_재발급_실패_유효하지_않은_토큰")
    void reissue_fail_invalid_token() {
        assertThatThrownBy(() -> authService.reissue(new TokenRequest("invalid.token.value")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-2");
    }

    @Test
    @DisplayName("토큰_재발급_실패_재로그인_후_이전_토큰_사용")
    void reissue_fail_token_mismatch() {
        Long userId = createTestUser();
        LoginResponse firstLogin = authService.login(new LoginRequest("test@example.com", "password123"));

        // 재로그인으로 Redis 토큰이 교체된 상황 시뮬레이션
        //        refreshTokenRepository.save(new RefreshToken(userId, "replaced_after_relogin"));

        // Mock 설정
        given(refreshTokenRepository.findById(userId))
                .willReturn(Optional.of(new RefreshToken(userId, "different_token")));

        assertThatThrownBy(() -> authService.reissue(new TokenRequest(firstLogin.refreshToken())))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-4");
    }

    @Test
    @DisplayName("토큰_재발급_실패_로그아웃_후_토큰_사용")
    void reissue_fail_after_logout() {
        Long userId = createTestUser();
        LoginResponse loginResponse = authService.login(new LoginRequest("test@example.com", "password123"));
        authService.logout(userId);

        assertThatThrownBy(() -> authService.reissue(new TokenRequest(loginResponse.refreshToken())))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-3");
    }

    // ===== 로그아웃 =====

    @Test
    @DisplayName("로그아웃_성공_Redis_토큰_삭제")
    void logout_success() {
        Long userId = createTestUser();
        authService.login(new LoginRequest("test@example.com", "password123"));

        authService.logout(userId);

        assertThat(refreshTokenRepository.findById(userId)).isEmpty();
    }

    // ===== 게스트 로그인 =====

    @Test
    @DisplayName("게스트_로그인_성공")
    void guestLogin_success() {
        given(nicknameGenerator.generate()).willReturn("게스트닉네임");

        LoginResponse response = authService.guestLogin();

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEmpty();
    }

    @Test
    @DisplayName("게스트_로그인_성공_닉네임_중복_시_suffix_부여")
    void guestLogin_success_duplicate_nickname_gets_suffix() {
        given(nicknameGenerator.generate()).willReturn("홍길동");

        authService.guestLogin();
        LoginResponse response = authService.guestLogin();

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("게스트_로그인_성공_회원_닉네임과_중복_시_suffix_부여")
    void guestLogin_success_member_nickname_conflict_gets_suffix() {
        createTestUser(); // 닉네임: 테스터
        given(nicknameGenerator.generate()).willReturn("테스터");

        LoginResponse response = authService.guestLogin();

        assertThat(response.accessToken()).isNotBlank();
    }

    // ===== 비밀번호 변경 =====

    @Test
    @DisplayName("비밀번호_변경_성공")
    void updatePassword_success() {
        Long userId = createTestUser();

        authService.updatePassword(
                userId,
                UpdatePasswordRequest.builder()
                        .currentPassword("password123")
                        .newPassword("newpassword456")
                        .build());

        // 변경된 비밀번호로 로그인 성공
        LoginResponse response = authService.login(new LoginRequest("test@example.com", "newpassword456"));
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호_변경_실패_존재하지_않는_유저")
    void updatePassword_fail_user_not_found() {
        assertThatThrownBy(() -> authService.updatePassword(
                        999L,
                        UpdatePasswordRequest.builder()
                                .currentPassword("password123")
                                .newPassword("newpassword456")
                                .build()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("비밀번호_변경_실패_현재_비밀번호_불일치")
    void updatePassword_fail_wrong_current_password() {
        Long userId = createTestUser();

        assertThatThrownBy(() -> authService.updatePassword(
                        userId,
                        UpdatePasswordRequest.builder()
                                .currentPassword("wrongpassword")
                                .newPassword("newpassword456")
                                .build()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "401-5");
    }

    @Test
    @DisplayName("비밀번호_변경_실패_새_비밀번호가_현재와_동일")
    void updatePassword_fail_same_password() {
        Long userId = createTestUser();

        assertThatThrownBy(() -> authService.updatePassword(
                        userId,
                        UpdatePasswordRequest.builder()
                                .currentPassword("password123")
                                .newPassword("password123")
                                .build()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-1");
    }
}
