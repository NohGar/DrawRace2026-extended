package backend.drawrace.domain.user.controller;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import backend.drawrace.domain.user.dto.UpdateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.dto.UserSearchResponse;
import backend.drawrace.domain.user.service.UserService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "User API", description = "유저 정보 조회, 검색 및 프로필 관리")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @Operation(summary = "본인 정보 조회", description = "현재 로그인한 유저의 프로필 및 승률 통계를 조회합니다.")
    @GetMapping("/me")
    public RsData<UserInfoResponse> getMyInfo(@AuthenticationPrincipal SecurityUser securityUser) {
        UserInfoResponse response = userService.getUser(securityUser.getUserId());
        return new RsData<>("200-1", "본인 정보를 성공적으로 조회했습니다.", response);
    }

    @Operation(summary = "특정 유저 정보 조회", description = "유저 ID를 통해 프로필 정보를 조회합니다.")
    @GetMapping("/{userId}")
    public RsData<UserInfoResponse> findOne(@PathVariable Long userId) {
        UserInfoResponse response = userService.getUser(userId);
        return new RsData<>("200-2", "유저 정보를 성공적으로 조회했습니다.", response);
    }

    @Operation(summary = "닉네임으로 유저 검색", description = "닉네임이 정확히 일치하는 유저를 검색합니다.")
    @GetMapping("/search")
    public RsData<UserSearchResponse> searchByNickname(@RequestParam @NotBlank String nickname) {
        UserSearchResponse response = userService.searchByNickname(nickname);
        return new RsData<>("200-5", "유저를 성공적으로 조회했습니다.", response);
    }

    @Operation(summary = "프로필 수정", description = "닉네임이나 프로필 이미지를 변경합니다. 닉네임 중복 시 409 에러가 발생합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임(409-1)")
    })
    @PatchMapping("/me")
    public RsData<UserInfoResponse> updateMyProfile(
            @AuthenticationPrincipal SecurityUser securityUser, @RequestBody @Validated UpdateUserRequest request) {
        UserInfoResponse response = userService.updateProfile(securityUser.getUserId(), request);
        return new RsData<>("200-4", "프로필이 성공적으로 수정되었습니다.", response);
    }

    @Operation(summary = "프로필 이미지 업로드", description = "이미지 파일을 업로드하여 프로필 이미지를 교체합니다. (jpg, jpeg, png, gif, webp)")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RsData<UserInfoResponse> updateProfileImage(
            @AuthenticationPrincipal SecurityUser securityUser, @RequestPart MultipartFile image) {
        UserInfoResponse response = userService.updateProfileImage(securityUser.getUserId(), image);
        return new RsData<>("200-6", "프로필 이미지가 성공적으로 변경되었습니다.", response);
    }

    @Operation(summary = "회원 탈퇴", description = "계정을 삭제하고 세션(리프레시 토큰)을 만료시킵니다.")
    @DeleteMapping("/me")
    public RsData<Void> deleteMyAccount(@AuthenticationPrincipal SecurityUser securityUser) {
        userService.deleteUser(securityUser.getUserId());
        return new RsData<>("200-3", "회원탈퇴가 완료되었습니다.");
    }
}
