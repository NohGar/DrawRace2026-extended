package backend.drawrace.domain.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.chat.service.AiChatService;
import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.dto.response.GetRoomListRes;
import backend.drawrace.domain.room.dto.response.RankingRes;
import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.dto.response.RoomInviteResponse;
import backend.drawrace.domain.room.dto.response.RoomUpdateResponse;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.FriendshipStatus;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.entity.UserStats;
import backend.drawrace.domain.user.repository.FriendshipRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private RankingService rankingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<AiChatService> aiChatServiceProvider;

    @InjectMocks
    private RoomService roomService;

    @Test
    @DisplayName("방 생성 성공 - 인원은 4명으로 고정된다")
    void createRoom_success() throws Exception {
        Long userId = 1L;
        CreateRoomReq req = new CreateRoomReq("테스트 방", (short) 4, (short) 3, "1234");

        User user = User.builder()
                .email("test@test.com")
                .nickname("유저A")
                .isAi(false)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(roomRepository.save(any(Room.class))).willAnswer(inv -> {
            Room room = inv.getArgument(0);
            setField(room, "id", 100L);
            return room;
        });

        given(roomRepository.findById(100L)).willReturn(Optional.of(createRoom(100L, "테스트 방", userId)));

        RoomInfoRes res = roomService.createRoom(req, userId);

        assertThat(res.title()).isEqualTo("테스트 방");
        assertThat(res.maxPlayers()).isEqualTo((short) 4);
        assertThat(res.hostId()).isEqualTo(userId);

        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    @DisplayName("방 입장 실패 - 비밀번호가 틀리면 예외가 발생한다")
    void joinRoom_fail_password() {
        Long roomId = 100L;

        Room room = Room.builder()
                .title("비번방")
                .password("1234")
                .maxPlayers((short) 4)
                .build();

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom(roomId, 2L, "wrong_pw"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-4");
    }

    @Test
    @DisplayName("입장 시 동시성 충돌이 발생하면 최대 3번까지 재시도한다")
    void shouldRetryWhenOptimisticLockConflictOccurs() throws Exception {
        Long roomId = 1L;
        Long userId = 1L;

        Room room = createRoom(roomId, "테스트방", 2L);
        User user = createUser(userId, "채은");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, userId))
                .willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        doThrow(ObjectOptimisticLockingFailureException.class)
                .doThrow(ObjectOptimisticLockingFailureException.class)
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(participantRepository)
                .save(any());

        roomService.joinRoom(roomId, userId, null);

        verify(roomRepository, times(3)).findById(roomId);
        verify(participantRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("대기방에서 유저가 방을 나가면 실시간 업데이트 응답을 반환하고 참가자를 삭제한다")
    void leaveRoom_ReturnRoomUpdateResponse_BeforeGame() throws Exception {
        Long roomId = 10L;
        Long userId = 1L;
        Long otherUserId = 2L;

        Room room = createRoom(roomId, "테스트방", otherUserId);
        setField(room, "curPlayers", (short) 2);

        User user = createUser(userId, "유저A");
        User otherUser = createUser(otherUserId, "유저B");

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();
        setField(participant, "id", 100L);

        Participant otherParticipant =
                Participant.builder().userId(otherUser).room(room).isHost(true).build();
        setField(otherParticipant, "id", 101L);

        room.getParticipants().add(participant);
        room.getParticipants().add(otherParticipant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.of(participant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(otherParticipant));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getType()).isEqualTo("USER_LEAVE");
        assertThat(response.getLeaverId()).isEqualTo(userId);
        assertThat(response.getNewHostId()).isEqualTo(otherUserId);
        assertThat(response.getParticipants()).containsExactly("유저B");
        assertThat(response.getMessage()).contains("유저A님이 퇴장하셨습니다.");

        verify(participantRepository).delete(participant);
        verify(roomRepository, never()).delete(room);

        assertThat(room.getParticipants()).doesNotContain(participant);
        assertThat(room.getParticipants()).contains(otherParticipant);
    }

    @Test
    @DisplayName("게임 중 비방장 유저가 퇴장하면 참가자를 삭제하지 않고 퇴장 상태로 변경한다")
    void leaveRoom_DuringGame_MarkParticipantLeftWithoutDelete() throws Exception {
        Long roomId = 10L;
        Long userId = 1L;
        Long hostId = 2L;

        Room room = createRoom(roomId, "게임방", hostId);
        setField(room, "isPlaying", true);
        setField(room, "curPlayers", (short) 2);

        User user = createUser(userId, "유저A");
        User hostUser = createUser(hostId, "방장");

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();
        setField(participant, "id", 100L);

        Participant hostParticipant =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        setField(hostParticipant, "id", 101L);

        room.getParticipants().add(participant);
        room.getParticipants().add(hostParticipant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.of(participant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(hostParticipant));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getType()).isEqualTo("USER_LEAVE");
        assertThat(response.getMessage()).contains("유저A님이 퇴장하셨습니다.");

        assertThat(participant.isLeft()).isTrue();
        assertThat(room.getCurPlayers()).isEqualTo((short) 1);
        assertThat(room.getParticipants()).contains(participant);

        verify(participantRepository, never()).delete(any(Participant.class));
        verify(roomRepository, never()).delete(any(Room.class));
    }

    @Test
    @DisplayName("게임 종료 성공 - 우승자 마킹 및 스탯이 업데이트된다")
    void finishGame_success() throws Exception {
        Long roomId = 100L;
        Long winnerId = 1L;

        Room room = createRoom(roomId, "게임방", winnerId);
        setField(room, "isPlaying", true);

        User winnerUser = createUser(winnerId, "우승자");
        UserStats stats = UserStats.builder().user(winnerUser).build();
        setField(winnerUser, "stats", stats);

        Participant participant = Participant.builder()
                .userId(winnerUser)
                .room(room)
                .roundWinCount(3)
                .build();

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        given(rankingService.getRankingList(roomId)).willReturn(Set.of(tuple));
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant));

        roomService.finishGame(roomId);

        assertThat(room.isPlaying()).isFalse();
        assertThat(participant.isWinner()).isTrue();
        assertThat(stats.getTotalGameCount()).isEqualTo(1);
        assertThat(stats.getWinGameCount()).isEqualTo(1);

        verify(rankingService).clearRanking(roomId);
    }

    @Test
    @DisplayName("방 퇴장 성공 - 방장이 나가면 다음 사람에게 위임된다")
    void leaveRoom_hostDelegation() throws Exception {
        Long roomId = 100L;
        Long hostId = 1L;
        Long nextHostId = 2L;

        Room room = createRoom(roomId, "위임테스트", hostId);
        setField(room, "curPlayers", (short) 2);

        User hostUser = createUser(hostId, "방장");
        User nextUser = createUser(nextHostId, "다음방장");

        Participant hostPart =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        setField(hostPart, "id", 100L);

        Participant nextPart =
                Participant.builder().userId(nextUser).room(room).isHost(false).build();
        setField(nextPart, "id", 101L);

        room.getParticipants().add(hostPart);
        room.getParticipants().add(nextPart);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, hostId)).willReturn(Optional.of(hostPart));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(nextPart));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, hostId);

        assertThat(response).isNotNull();
        assertThat(room.getHostId()).isEqualTo(nextHostId);
        assertThat(nextPart.isHost()).isTrue();

        verify(participantRepository).delete(hostPart);
        verify(roomRepository, never()).delete(room);
    }

    @Test
    @DisplayName("게임 중 방장이 퇴장하면 다음 사람에게 위임하고 방장은 퇴장 상태로 변경한다")
    void leaveRoom_hostDelegation_DuringGame() throws Exception {
        Long roomId = 100L;
        Long hostId = 1L;
        Long nextHostId = 2L;

        Room room = createRoom(roomId, "게임중위임테스트", hostId);
        setField(room, "isPlaying", true);
        setField(room, "curPlayers", (short) 2);

        User hostUser = createUser(hostId, "방장");
        User nextUser = createUser(nextHostId, "다음방장");

        Participant hostPart =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        setField(hostPart, "id", 100L);

        Participant nextPart =
                Participant.builder().userId(nextUser).room(room).isHost(false).build();
        setField(nextPart, "id", 101L);

        room.getParticipants().add(hostPart);
        room.getParticipants().add(nextPart);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, hostId)).willReturn(Optional.of(hostPart));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(nextPart));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, hostId);

        assertThat(response).isNotNull();
        assertThat(room.getHostId()).isEqualTo(nextHostId);
        assertThat(nextPart.isHost()).isTrue();

        assertThat(hostPart.isLeft()).isTrue();
        assertThat(hostPart.isHost()).isFalse();
        assertThat(room.getParticipants()).contains(hostPart);

        verify(participantRepository, never()).delete(any(Participant.class));
        verify(roomRepository, never()).delete(any(Room.class));
    }

    @Test
    @DisplayName("유저 퇴장 시 퇴장 알림과 방장 위임 알림이 채팅창에 전달된다")
    void leaveRoom_ShouldSendSystemNotice() throws Exception {
        Long userId = 1L;
        Long roomId = 10L;
        Long nextHostId = 2L;

        Room room = createRoom(roomId, "알림테스트", userId);
        setField(room, "curPlayers", (short) 2);

        User user = createUser(userId, "유저A");
        User nextUser = createUser(nextHostId, "다음방장");

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(true).build();
        setField(participant, "id", 100L);

        Participant nextHost =
                Participant.builder().userId(nextUser).room(room).isHost(false).build();
        setField(nextHost, "id", 101L);

        room.getParticipants().add(participant);
        room.getParticipants().add(nextHost);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.of(participant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(nextHost));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        roomService.leaveRoom(roomId, userId);

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.NOTICE
                                && dto.getMessage().contains("유저A님이 퇴장하셨습니다.")));

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.NOTICE
                                && dto.getMessage().contains("방장이 다음방장님으로 변경되었습니다.")));
    }

    @Test
    @DisplayName("웹소켓 연결 종료 시 현재 참가 중인 방에서 퇴장 처리한다")
    void leaveCurrentRoom_success() throws Exception {
        Long roomId = 10L;
        Long userId = 1L;
        Long otherUserId = 2L;

        Room room = createRoom(roomId, "웹소켓퇴장", otherUserId);
        setField(room, "curPlayers", (short) 2);

        User user = createUser(userId, "유저A");
        User otherUser = createUser(otherUserId, "유저B");

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();
        setField(participant, "id", 100L);

        Participant otherParticipant =
                Participant.builder().userId(otherUser).room(room).isHost(true).build();
        setField(otherParticipant, "id", 101L);

        room.getParticipants().add(participant);
        room.getParticipants().add(otherParticipant);

        given(participantRepository.findFirstByUserId_IdAndIsLeftFalseOrderByIdDesc(userId))
                .willReturn(Optional.of(participant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(otherParticipant));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(1L);

        RoomUpdateResponse response = roomService.leaveCurrentRoom(userId);

        assertThat(response).isNotNull();
        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getType()).isEqualTo("USER_LEAVE");
        assertThat(response.getLeaverId()).isEqualTo(userId);
        assertThat(response.getNewHostId()).isEqualTo(otherUserId);
        assertThat(response.getParticipants()).containsExactly("유저B");
        assertThat(response.getMessage()).contains("유저A님이 퇴장하셨습니다.");

        verify(participantRepository).delete(participant);
        verify(roomRepository, never()).delete(room);

        assertThat(room.getParticipants()).doesNotContain(participant);
        assertThat(room.getParticipants()).contains(otherParticipant);
    }

    @Test
    @DisplayName("웹소켓 연결 종료 시 현재 참가 중인 방이 없으면 null을 반환한다")
    void leaveCurrentRoom_noActiveParticipant_returnNull() {
        Long userId = 1L;

        given(participantRepository.findFirstByUserId_IdAndIsLeftFalseOrderByIdDesc(userId))
                .willReturn(Optional.empty());

        RoomUpdateResponse response = roomService.leaveCurrentRoom(userId);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("게임 중 호스트 퇴장 후 마지막 유저가 퇴장하면 방이 삭제된다")
    void leaveRoom_lastUserLeaveDuringGame_roomDeleted() throws Exception {
        Long roomId = 10L;
        Long lastUserId = 2L;

        Room room = createRoom(roomId, "게임방", lastUserId);
        setField(room, "isPlaying", true);
        setField(room, "curPlayers", (short) 1);

        User lastUser = createUser(lastUserId, "마지막유저");

        Participant lastParticipant =
                Participant.builder().userId(lastUser).room(room).isHost(true).build();
        setField(lastParticipant, "id", 200L);

        room.getParticipants().add(lastParticipant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, lastUserId))
                .willReturn(Optional.of(lastParticipant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of());
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(0L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, lastUserId);

        assertThat(response).isNull();

        verify(roundSubmissionRepository).deleteByRoomId(roomId);
        verify(roundParticipantRepository).deleteByRoomId(roomId);
        verify(roundRepository).deleteByRoomId(roomId);
        verify(rankingService).clearRanking(roomId);
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("게임 전 마지막 유저 퇴장 시 방이 삭제된다")
    void leaveRoom_lastUserLeaveBeforeGame_roomDeleted() throws Exception {
        Long roomId = 10L;
        Long hostId = 1L;

        Room room = createRoom(roomId, "대기방", hostId);

        User hostUser = createUser(hostId, "방장");

        Participant hostParticipant =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        setField(hostParticipant, "id", 100L);

        room.getParticipants().add(hostParticipant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, hostId)).willReturn(Optional.of(hostParticipant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of());
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(0L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, hostId);

        assertThat(response).isNull();

        verify(participantRepository).delete(hostParticipant);
        verify(roundSubmissionRepository).deleteByRoomId(roomId);
        verify(roundParticipantRepository).deleteByRoomId(roomId);
        verify(roundRepository).deleteByRoomId(roomId);
        verify(rankingService).clearRanking(roomId);
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("게임 중 방장 퇴장 시 AI만 남으면 방이 삭제된다")
    void leaveRoom_hostLeaveDuringGame_onlyAiRemains_roomDeleted() throws Exception {
        Long roomId = 10L;
        Long hostId = 1L;

        Room room = createRoom(roomId, "게임방", hostId);
        setField(room, "isPlaying", true);
        setField(room, "curPlayers", (short) 2);

        User hostUser = createUser(hostId, "방장");
        User aiUser = User.builder().nickname("AI").isAi(true).build();
        setField(aiUser, "id", 99L);

        Participant hostParticipant =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        setField(hostParticipant, "id", 100L);

        Participant aiParticipant =
                Participant.builder().userId(aiUser).room(room).isHost(false).build();
        setField(aiParticipant, "id", 101L);

        room.getParticipants().add(hostParticipant);
        room.getParticipants().add(aiParticipant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, hostId)).willReturn(Optional.of(hostParticipant));
        given(participantRepository.findActiveParticipantsByRoomId(roomId)).willReturn(List.of(aiParticipant));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(0L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, hostId);

        assertThat(response).isNull();

        verify(roundSubmissionRepository).deleteByRoomId(roomId);
        verify(roundParticipantRepository).deleteByRoomId(roomId);
        verify(roundRepository).deleteByRoomId(roomId);
        verify(rankingService).clearRanking(roomId);
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("이미 퇴장 처리된 참가자가 다시 leaveRoom을 호출해도 인간 유저가 없으면 방이 삭제된다")
    void leaveRoom_alreadyLeftParticipant_cleanupRoomIfNoActiveHuman() throws Exception {
        Long roomId = 10L;
        Long userId = 1L;

        Room room = createRoom(roomId, "게임방", userId);
        setField(room, "isPlaying", true);

        User user = createUser(userId, "방장");

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(true).build();
        setField(participant, "id", 100L);
        participant.leave();

        room.getParticipants().add(participant);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.of(participant));
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(0L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, userId);

        assertThat(response).isNull();

        verify(roundSubmissionRepository).deleteByRoomId(roomId);
        verify(roundParticipantRepository).deleteByRoomId(roomId);
        verify(roundRepository).deleteByRoomId(roomId);
        verify(rankingService).clearRanking(roomId);
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("참가자 정보가 없어도 방에 인간 유저가 없으면 cleanup을 통해 방이 삭제된다")
    void leaveRoom_participantNotFound_cleanupRoomIfNoActiveHuman() throws Exception {
        Long roomId = 10L;
        Long userId = 1L;

        Room room = createRoom(roomId, "유령방", userId);
        setField(room, "isPlaying", true);

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.empty());
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countActiveHumanByRoomId(roomId)).willReturn(0L);

        RoomUpdateResponse response = roomService.leaveRoom(roomId, userId);

        assertThat(response).isNull();

        verify(roundSubmissionRepository).deleteByRoomId(roomId);
        verify(roundParticipantRepository).deleteByRoomId(roomId);
        verify(roundRepository).deleteByRoomId(roomId);
        verify(rankingService).clearRanking(roomId);
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("목록 조회 시 curPlayers는 실제 활성 인원(isLeft=false) 기준으로 반환된다")
    void getRoomList_curPlayersReflectsActiveParticipantCount() throws Exception {
        Long roomId = 1L;
        Long hostId = 1L;

        Room room = createRoom(roomId, "테스트방", hostId);
        setField(room, "curPlayers", (short) 2);

        User hostUser = createUser(hostId, "방장");
        User leftUser = createUser(2L, "퇴장유저");

        Participant hostParticipant =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();

        Participant leftParticipant =
                Participant.builder().userId(leftUser).room(room).isHost(false).build();
        leftParticipant.leave();

        room.getParticipants().add(hostParticipant);
        room.getParticipants().add(leftParticipant);

        given(roomRepository.findAll()).willReturn(List.of(room));

        List<GetRoomListRes> result = roomService.getRoomList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).curPlayers()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("목록 조회 시 활성 인간 참가자가 없는 방은 반환되지 않는다")
    void getRoomList_excludesRoomsWithNoActiveHumanParticipants() throws Exception {
        Long roomId = 1L;

        Room ghostRoom = createRoom(roomId, "유령방", 1L);

        User user = createUser(1L, "유저A");

        Participant leftParticipant =
                Participant.builder().userId(user).room(ghostRoom).isHost(false).build();
        leftParticipant.leave();

        ghostRoom.getParticipants().add(leftParticipant);

        given(roomRepository.findAll()).willReturn(List.of(ghostRoom));

        List<GetRoomListRes> result = roomService.getRoomList();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최종 랭킹 조회 시 Redis에 데이터가 있으면 DB 전체 조회를 생략하고 Redis 데이터를 반환한다")
    void getFinalRanking_PriorityRedis() throws Exception {
        Long roomId = 100L;
        Long userId = 1L;

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        given(tuple.getValue()).willReturn(String.valueOf(userId));
        given(tuple.getScore()).willReturn(3.0);
        given(rankingService.getRankingList(roomId)).willReturn(Set.of(tuple));

        User user = createUser(userId, "우승자");
        Participant participant = Participant.builder().userId(user).build();

        given(participantRepository.findByRoomIdAndUserId_Id(roomId, userId)).willReturn(Optional.of(participant));

        List<RankingRes> result = roomService.getFinalRanking(roomId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nickname()).isEqualTo("우승자");
        assertThat(result.get(0).roundWinCount()).isEqualTo(3);

        verify(participantRepository, never()).findByRoomId(roomId);
    }

    @Test
    @DisplayName("최종 랭킹 조회 시 Redis가 비어있으면 DB에서 데이터를 조회하는 Fallback 로직이 작동한다")
    void getFinalRanking_FallbackToDb() throws Exception {
        Long roomId = 100L;

        given(rankingService.getRankingList(roomId)).willReturn(Set.of());

        User user = createUser(1L, "DB유저");

        Participant participant =
                Participant.builder().userId(user).roundWinCount(2).build();

        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant));

        List<RankingRes> result = roomService.getFinalRanking(roomId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nickname()).isEqualTo("DB유저");

        verify(participantRepository).findByRoomId(roomId);
    }

    // ===== inviteFriend() =====

    @Test
    @DisplayName("친구 초대 성공 - WebSocket 알림이 전송된다")
    void inviteFriend_success() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        User inviter = createUser(inviterId, "초대자");
        User friend = createUser(friendId, "친구");
        Friendship friendship = Friendship.builder()
                .requester(inviter).receiver(friend).status(FriendshipStatus.ACCEPTED).build();

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(true);
        given(userRepository.findById(inviterId)).willReturn(Optional.of(inviter));
        given(userRepository.findById(friendId)).willReturn(Optional.of(friend));
        given(friendshipRepository.findByUsers(inviter, friend)).willReturn(Optional.of(friendship));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, friendId)).willReturn(false);

        roomService.inviteFriend(roomId, inviterId, friendId);

        verify(messagingTemplate).convertAndSend(
                eq("/sub/users/" + friendId + "/notifications"),
                any(RoomInviteResponse.class));
    }

    @Test
    @DisplayName("친구 초대 실패 - 방 참여자가 아니면 예외가 발생한다")
    void inviteFriend_fail_notParticipant() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(false);

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-2");
    }

    @Test
    @DisplayName("친구 초대 실패 - 게임 중이면 예외가 발생한다")
    void inviteFriend_fail_gamePlaying() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        setField(room, "isPlaying", true);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(true);

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-2");
    }

    @Test
    @DisplayName("친구 초대 실패 - 방 정원이 초과되면 예외가 발생한다")
    void inviteFriend_fail_roomFull() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        setField(room, "curPlayers", (short) 4);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(true);

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-3");
    }

    @Test
    @DisplayName("친구 초대 실패 - 친구 관계가 아니면 예외가 발생한다")
    void inviteFriend_fail_notFriend() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        User inviter = createUser(inviterId, "초대자");
        User friend = createUser(friendId, "타인");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(true);
        given(userRepository.findById(inviterId)).willReturn(Optional.of(inviter));
        given(userRepository.findById(friendId)).willReturn(Optional.of(friend));
        given(friendshipRepository.findByUsers(inviter, friend)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-3");
    }

    @Test
    @DisplayName("친구 초대 실패 - PENDING 상태 친구는 초대할 수 없다")
    void inviteFriend_fail_pendingFriendship() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        User inviter = createUser(inviterId, "초대자");
        User friend = createUser(friendId, "대기중친구");
        Friendship friendship = Friendship.builder()
                .requester(inviter).receiver(friend).status(FriendshipStatus.PENDING).build();

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)).willReturn(true);
        given(userRepository.findById(inviterId)).willReturn(Optional.of(inviter));
        given(userRepository.findById(friendId)).willReturn(Optional.of(friend));
        given(friendshipRepository.findByUsers(inviter, friend)).willReturn(Optional.of(friendship));

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "403-3");
    }

    @Test
    @DisplayName("친구 초대 실패 - 이미 방에 참여 중인 친구는 초대할 수 없다")
    void inviteFriend_fail_friendAlreadyInRoom() throws Exception {
        Long roomId = 1L;
        Long inviterId = 1L;
        Long friendId = 2L;

        Room room = createRoom(roomId, "테스트방", inviterId);
        User inviter = createUser(inviterId, "초대자");
        User friend = createUser(friendId, "이미참여중친구");
        Friendship friendship = Friendship.builder()
                .requester(inviter).receiver(friend).status(FriendshipStatus.ACCEPTED).build();

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(eq(roomId), eq(inviterId))).willReturn(true);
        given(userRepository.findById(inviterId)).willReturn(Optional.of(inviter));
        given(userRepository.findById(friendId)).willReturn(Optional.of(friend));
        given(friendshipRepository.findByUsers(inviter, friend)).willReturn(Optional.of(friendship));
        given(participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(eq(roomId), eq(friendId))).willReturn(true);

        assertThatThrownBy(() -> roomService.inviteFriend(roomId, inviterId, friendId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-6");
    }

    private Room createRoom(Long id, String title, Long hostId) throws Exception {
        Room room = Room.builder()
                .title(title)
                .hostId(hostId)
                .maxPlayers((short) 4)
                .curPlayers((short) 1)
                .participants(new ArrayList<>())
                .build();

        setField(room, "id", id);

        return room;
    }

    private User createUser(Long id, String nickname) throws Exception {
        User user = User.builder().nickname(nickname).isAi(false).build();

        setField(user, "id", id);

        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
