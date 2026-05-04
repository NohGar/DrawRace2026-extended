package backend.drawrace.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import backend.drawrace.domain.user.dto.UpdateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.domain.user.dto.UserSearchResponse;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.RefreshTokenRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;
import backend.drawrace.global.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FileStorageService fileStorageService;

    @Override
    public UserInfoResponse getUser(Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId));

        return UserInfoResponse.from(user);
    }

    @Override
    public User findById(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId));
    }

    @Override
    public UserSearchResponse searchByNickname(String nickname) {
        User user = userRepository
                .findByNickname(nickname)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다."));
        return UserSearchResponse.from(user);
    }

    @Override
    @Transactional
    public UserInfoResponse updateProfile(Long userId, UpdateUserRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId));

        if (user.isGuest()) {
            throw new ServiceException("403-4", "게스트는 프로필을 수정할 수 없습니다.");
        }

        if (request.nickname() != null && userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new ServiceException("409-1", "이미 사용 중인 닉네임입니다.");
        }

        user.updateProfile(request.nickname(), request.profileImageUrl());
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public UserInfoResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId));

        if (user.isGuest()) {
            throw new ServiceException("403-4", "게스트는 프로필을 수정할 수 없습니다.");
        }

        if (user.getProfileImageUrl() != null) {
            fileStorageService.delete(user.getProfileImageUrl());
        }

        String imageUrl = fileStorageService.store(image);
        user.updateProfile(null, imageUrl);
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ServiceException("404-1", "존재하지 않는 유저입니다. ID: " + userId);
        }
        refreshTokenRepository.deleteById(userId);
        userRepository.deleteById(userId);
    }
}
