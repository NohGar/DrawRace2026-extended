package backend.drawrace.domain.user.service;

import java.util.List;

import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;

public interface FriendshipService {

    /**
     * 친구 요청 보내기
     * @param requesterId 요청자 ID
     * @param receiverId 수신자 ID
     */
    void sendFriendRequest(Long requesterId, Long receiverId);

    /**
     * 친구 요청 수락
     * @param receiverId 수신자 ID (본인 확인용)
     * @param friendshipId 수락할 친구 요청 ID
     */
    void acceptFriendRequest(Long receiverId, Long friendshipId);

    /**
     * 친구 요청 거절 (요청 삭제)
     * @param receiverId 수신자 ID (본인 확인용)
     * @param friendshipId 거절할 친구 요청 ID
     */
    void rejectFriendRequest(Long receiverId, Long friendshipId);

    /**
     * 받은 친구 요청 목록 조회 (PENDING 상태)
     * @param receiverId 수신자 ID
     * @return 요청자 정보 목록
     */
    List<FriendRequestResponse> getReceivedRequests(Long receiverId);

    /**
     * 보낸 친구 요청 목록 조회 (PENDING 상태)
     * @param requesterId 요청자 ID
     * @return 수신자 정보 목록
     */
    List<FriendRequestResponse> getSentRequests(Long requesterId);

    /**
     * 친구 삭제
     * @param userId 요청자 ID
     * @param friendId 삭제할 친구 ID
     */
    void deleteFriend(Long userId, Long friendId);

    /**
     * 친구 목록 조회
     * @param userId 유저 ID
     * @return 친구 정보 목록
     */
    List<FriendInfoResponse> getFriendList(Long userId);
}
