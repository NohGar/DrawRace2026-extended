package backend.drawrace.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;
import backend.drawrace.global.exception.ServiceException;

@SpringBootTest
@Transactional
class FriendshipServiceTest {

    @Autowired
    FriendshipService friendshipService;

    @Autowired
    AuthService authService;

    private Long userAId;
    private Long userBId;
    private Long userCId;

    @BeforeEach
    void setUp() {
        userAId = authService.signup(new CreateUserRequest("a@example.com", "password123", "유저A"));
        userBId = authService.signup(new CreateUserRequest("b@example.com", "password123", "유저B"));
        userCId = authService.signup(new CreateUserRequest("c@example.com", "password123", "유저C"));
    }

    // ===== 친구 요청 보내기 =====

    @Test
    @DisplayName("친구_요청_보내기_성공")
    void sendFriendRequest_success() {
        friendshipService.sendFriendRequest(userAId, userBId);

        List<FriendRequestResponse> received = friendshipService.getReceivedRequests(userBId);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).userId()).isEqualTo(userAId);
    }

    @Test
    @DisplayName("친구_요청_보내기_실패_자기_자신에게")
    void sendFriendRequest_fail_self() {
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(userAId, userAId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-1");
    }

    @Test
    @DisplayName("친구_요청_보내기_실패_중복_요청")
    void sendFriendRequest_fail_duplicate() {
        friendshipService.sendFriendRequest(userAId, userBId);

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(userAId, userBId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-1");
    }

    @Test
    @DisplayName("친구_요청_보내기_실패_역방향_중복_요청")
    void sendFriendRequest_fail_reverse_duplicate() {
        friendshipService.sendFriendRequest(userAId, userBId);

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(userBId, userAId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-1");
    }

    // ===== 친구 요청 수락 =====

    @Test
    @DisplayName("친구_요청_수락_성공")
    void acceptFriendRequest_success() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();

        friendshipService.acceptFriendRequest(userBId, friendshipId);

        List<FriendInfoResponse> friends = friendshipService.getFriendList(userBId);
        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).nickname()).isEqualTo("유저A");
    }

    @Test
    @DisplayName("친구_요청_수락_실패_존재하지_않는_요청")
    void acceptFriendRequest_fail_not_found() {
        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(userBId, 999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("친구_요청_수락_실패_권한_없음")
    void acceptFriendRequest_fail_forbidden() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();

        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(userCId, friendshipId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-1");
    }

    @Test
    @DisplayName("친구_요청_수락_실패_이미_처리된_요청")
    void acceptFriendRequest_fail_already_processed() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();
        friendshipService.acceptFriendRequest(userBId, friendshipId);

        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(userBId, friendshipId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-2");
    }

    // ===== 친구 요청 거절 =====

    @Test
    @DisplayName("친구_요청_거절_성공")
    void rejectFriendRequest_success() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();

        friendshipService.rejectFriendRequest(userBId, friendshipId);

        assertThat(friendshipService.getReceivedRequests(userBId)).isEmpty();
    }

    @Test
    @DisplayName("친구_요청_거절_실패_이미_수락된_요청")
    void rejectFriendRequest_fail_already_accepted() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();
        friendshipService.acceptFriendRequest(userBId, friendshipId);

        assertThatThrownBy(() -> friendshipService.rejectFriendRequest(userBId, friendshipId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-4");
    }

    @Test
    @DisplayName("친구_요청_거절_실패_권한_없음")
    void rejectFriendRequest_fail_forbidden() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();

        assertThatThrownBy(() -> friendshipService.rejectFriendRequest(userCId, friendshipId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-2");
    }

    // ===== 친구 삭제 =====

    @Test
    @DisplayName("친구_삭제_성공")
    void deleteFriend_success() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();
        friendshipService.acceptFriendRequest(userBId, friendshipId);

        friendshipService.deleteFriend(userAId, userBId);

        assertThat(friendshipService.getFriendList(userAId)).isEmpty();
        assertThat(friendshipService.getFriendList(userBId)).isEmpty();
    }

    @Test
    @DisplayName("친구_삭제_실패_친구_관계_없음")
    void deleteFriend_fail_not_found() {
        assertThatThrownBy(() -> friendshipService.deleteFriend(userAId, userBId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-3");
    }

    @Test
    @DisplayName("친구_삭제_실패_수락되지_않은_관계")
    void deleteFriend_fail_not_accepted() {
        friendshipService.sendFriendRequest(userAId, userBId);

        assertThatThrownBy(() -> friendshipService.deleteFriend(userAId, userBId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "409-3");
    }

    // ===== 조회 =====

    @Test
    @DisplayName("보낸_요청_목록_조회_성공")
    void getSentRequests_success() {
        friendshipService.sendFriendRequest(userAId, userBId);
        friendshipService.sendFriendRequest(userAId, userCId);

        List<FriendRequestResponse> sent = friendshipService.getSentRequests(userAId);

        assertThat(sent).hasSize(2);
    }

    @Test
    @DisplayName("친구_목록_조회_성공")
    void getFriendList_success() {
        friendshipService.sendFriendRequest(userAId, userBId);
        Long friendshipId =
                friendshipService.getReceivedRequests(userBId).get(0).friendshipId();
        friendshipService.acceptFriendRequest(userBId, friendshipId);

        assertThat(friendshipService.getFriendList(userAId)).hasSize(1);
        assertThat(friendshipService.getFriendList(userBId)).hasSize(1);
    }
}
