package backend.drawrace.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;
import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.FriendshipStatus;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.FriendshipRepository;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;

    @Override
    @Transactional
    public void sendFriendRequest(Long requesterId, Long receiverId) {
        if (requesterId.equals(receiverId)) {
            throw new ServiceException("400-1", "자신에게 친구 요청을 보낼 수 없습니다.");
        }

        User requester = userService.findById(requesterId);
        User receiver = userService.findById(receiverId);

        if (requester.isGuest() || receiver.isGuest()) {
            throw new ServiceException("403-3", "게스트는 친구 기능을 사용할 수 없습니다.");
        }

        friendshipRepository.findByUsers(requester, receiver).ifPresent(f -> {
            throw new ServiceException("409-1", "이미 처리 중이거나 완료된 친구 요청이 있습니다.");
        });

        friendshipRepository.save(Friendship.builder()
                .requester(requester)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .build());
    }

    @Override
    @Transactional
    public void acceptFriendRequest(Long receiverId, Long friendshipId) {
        Friendship friendship = friendshipRepository
                .findById(friendshipId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 친구 요청입니다."));

        if (!friendship.getReceiver().getId().equals(receiverId)) {
            throw new ServiceException("403-1", "본인에게 온 요청만 수락할 수 있습니다.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ServiceException("409-2", "이미 처리된 요청입니다.");
        }

        friendship.updateStatus(FriendshipStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public void rejectFriendRequest(Long receiverId, Long friendshipId) {
        Friendship friendship = friendshipRepository
                .findById(friendshipId)
                .orElseThrow(() -> new ServiceException("404-2", "존재하지 않는 친구 요청입니다."));

        if (!friendship.getReceiver().getId().equals(receiverId)) {
            throw new ServiceException("403-2", "본인에게 온 요청만 거절할 수 있습니다.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ServiceException("409-4", "대기 중인 요청만 거절할 수 있습니다.");
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        Friendship friendship = friendshipRepository
                .findByUsers(user, friend)
                .orElseThrow(() -> new ServiceException("404-3", "친구 관계가 존재하지 않습니다."));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ServiceException("409-3", "수락된 친구 관계가 아닙니다.");
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getReceivedRequests(Long receiverId) {
        User receiver = userService.findById(receiverId);
        return friendshipRepository.findAllByReceiverAndStatus(receiver, FriendshipStatus.PENDING).stream()
                .map(f -> FriendRequestResponse.from(f, f.getRequester()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getSentRequests(Long requesterId) {
        User requester = userService.findById(requesterId);
        return friendshipRepository.findAllByRequesterAndStatus(requester, FriendshipStatus.PENDING).stream()
                .map(f -> FriendRequestResponse.from(f, f.getReceiver()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendInfoResponse> getFriendList(Long userId) {
        User user = userService.findById(userId);
        return friendshipRepository.findAllFriends(user).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(userId) ? f.getReceiver() : f.getRequester();
                    return FriendInfoResponse.from(friend);
                })
                .toList();
    }
}
