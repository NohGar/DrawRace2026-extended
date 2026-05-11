package backend.drawrace.domain.user.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;
import backend.drawrace.domain.user.service.FriendshipService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Friendship API", description = "친구 요청 및 목록 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friendship")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @Operation(summary = "친구 요청", description = "상대 유저 ID로 요청을 보냅니다. 본인에게는 불가합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "자신에게 요청(400-1)"),
        @ApiResponse(responseCode = "409", description = "이미 처리 중인 요청 존재(409-1)")
    })
    @PostMapping("/request/{receiverId}")
    public RsData<Void> sendFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long receiverId) {
        friendshipService.sendFriendRequest(securityUser.getUserId(), receiverId);
        return new RsData<>("201-1", "친구 요청을 보냈습니다.");
    }

    @Operation(summary = "친구 요청 수락")
    @PostMapping("/{friendshipId}/accept")
    public RsData<Void> acceptFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long friendshipId) {
        friendshipService.acceptFriendRequest(securityUser.getUserId(), friendshipId);
        return new RsData<>("200-1", "친구 요청을 수락했습니다.");
    }

    @Operation(summary = "친구 요청 거절")
    @DeleteMapping("/{friendshipId}/reject")
    public RsData<Void> rejectFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long friendshipId) {
        friendshipService.rejectFriendRequest(securityUser.getUserId(), friendshipId);
        return new RsData<>("200-2", "친구 요청을 거절했습니다.");
    }

    @Operation(summary = "친구 삭제")
    @DeleteMapping("/friends/{friendId}")
    public RsData<Void> deleteFriend(@AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long friendId) {
        friendshipService.deleteFriend(securityUser.getUserId(), friendId);
        return new RsData<>("200-6", "친구를 삭제했습니다.");
    }

    @Operation(summary = "받은 친구 요청 목록 조회")
    @GetMapping("/requests/received")
    public RsData<List<FriendRequestResponse>> getReceivedRequests(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendRequestResponse> requests = friendshipService.getReceivedRequests(securityUser.getUserId());
        return new RsData<>("200-3", "받은 친구 요청 목록을 조회했습니다.", requests);
    }

    @Operation(summary = "보낸 친구 요청 목록 조회")
    @GetMapping("/requests/sent")
    public RsData<List<FriendRequestResponse>> getSentRequests(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendRequestResponse> requests = friendshipService.getSentRequests(securityUser.getUserId());
        return new RsData<>("200-4", "보낸 친구 요청 목록을 조회했습니다.", requests);
    }

    @Operation(summary = "친구 목록 조회")
    @GetMapping
    public RsData<List<FriendInfoResponse>> getFriendList(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendInfoResponse> friends = friendshipService.getFriendList(securityUser.getUserId());
        return new RsData<>("200-5", "친구 목록을 조회했습니다.", friends);
    }
}
