package backend.drawrace.domain.user.service;

import org.springframework.web.multipart.MultipartFile;

import backend.drawrace.domain.user.dto.UpdateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.dto.UserSearchResponse;
import backend.drawrace.domain.user.entity.User;

public interface UserService {

    /**
     * 유저 단건 조회
     * @param userId 조회할 유저 ID
     * @return 해당 유저 정보 dto
     */
    UserInfoResponse getUser(Long userId);

    /**
     * 회원 탈퇴 (Refresh Token 및 유저 삭제)
     * @param userId 탈퇴할 유저 ID
     */
    void deleteUser(Long userId);

    /**
     * 닉네임으로 유저 검색 (정확히 일치)
     * @param nickname 검색할 닉네임
     * @return 해당 유저 검색 결과 dto
     */
    UserSearchResponse searchByNickname(String nickname);

    /**
     * 프로필 수정 (닉네임, 프로필 이미지 URL)
     * @param userId 수정할 유저 ID
     * @param request 수정 요청 DTO
     * @return 수정된 유저 정보 dto
     */
    UserInfoResponse updateProfile(Long userId, UpdateUserRequest request);

    /**
     * 프로필 이미지 업로드 및 교체
     * @param userId 수정할 유저 ID
     * @param image 업로드할 이미지 파일
     * @return 수정된 유저 정보 dto
     */
    UserInfoResponse updateProfileImage(Long userId, MultipartFile image);

    /**
     * 유저 엔티티 직접 조회 (서비스 간 내부 사용)
     * @param userId 조회할 유저 ID
     * @return User 엔티티
     */
    User findById(Long userId);
}
