package backend.drawrace.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.UpdateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.dto.UserSearchResponse;
import backend.drawrace.global.exception.ServiceException;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    AuthService authService;

    @MockBean
    GuestNicknameGenerator nicknameGenerator;

    private Long createTestUser() {
        return authService.signup(new CreateUserRequest("test@example.com", "password123", "테스터"));
    }

    @Test
    @DisplayName("유저_단건_조회_성공")
    void getUser_success() {
        Long savedId = createTestUser();

        UserInfoResponse response = userService.getUser(savedId);

        assertThat(response.id()).isEqualTo(savedId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("유저_단건_조회_실패_존재하지_않는_ID")
    void getUser_fail_not_found() {
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("회원_탈퇴_성공")
    void deleteUser_success() {
        Long savedId = createTestUser();

        userService.deleteUser(savedId);

        assertThatThrownBy(() -> userService.getUser(savedId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("회원_탈퇴_실패_존재하지_않는_ID")
    void deleteUser_fail_not_found() {
        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("닉네임으로_유저_검색_성공")
    void searchByNickname_success() {
        Long savedId = createTestUser();

        UserSearchResponse response = userService.searchByNickname("테스터");

        assertThat(response.id()).isEqualTo(savedId);
        assertThat(response.nickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("닉네임으로_유저_검색_실패_존재하지_않는_닉네임")
    void searchByNickname_fail_not_found() {
        assertThatThrownBy(() -> userService.searchByNickname("없는닉네임"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("프로필_수정_성공_닉네임_변경")
    void updateProfile_success_nickname_only() {
        Long savedId = createTestUser();

        UserInfoResponse response = userService.updateProfile(savedId, new UpdateUserRequest("새닉네임"));

        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("프로필_수정_실패_존재하지_않는_유저")
    void updateProfile_fail_not_found() {
        assertThatThrownBy(() -> userService.updateProfile(999L, new UpdateUserRequest("새닉네임")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("프로필_수정_실패_닉네임_중복")
    void updateProfile_fail_duplicate_nickname() {
        Long savedId = createTestUser();
        authService.signup(new CreateUserRequest("other@example.com", "password123", "다른유저"));

        assertThatThrownBy(() -> userService.updateProfile(savedId, new UpdateUserRequest("다른유저")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-1");
    }

    @Test
    @DisplayName("프로필_수정_성공_본인_닉네임_그대로_사용")
    void updateProfile_success_same_nickname() {
        Long savedId = createTestUser();

        UserInfoResponse response = userService.updateProfile(savedId, new UpdateUserRequest("테스터"));

        assertThat(response.nickname()).isEqualTo("테스터");
    }

    // ===== 게스트 제한 =====

    @Test
    @DisplayName("게스트_프로필_수정_실패")
    void updateProfile_fail_guest() {
        given(nicknameGenerator.generate()).willReturn("게스트테스터");
        authService.guestLogin();
        Long guestId = userService.searchByNickname("게스트테스터").id();

        assertThatThrownBy(() -> userService.updateProfile(guestId, new UpdateUserRequest("새닉네임")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-4");
    }
}
