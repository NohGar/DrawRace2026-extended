package backend.drawrace.domain.room.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import backend.drawrace.domain.user.repository.FriendshipRepository;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final RankingService rankingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;
    private final RoundSubmissionRepository roundSubmissionRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final RoundRepository roundRepository;

    // 최신 방 목록을 로비에 알림
    public void broadcastLobbyUpdate() {
        List<GetRoomListRes> roomList = getRoomList(); //
        messagingTemplate.convertAndSend("/sub/lobby", roomList); //
    }

    @Transactional
    public RoomInfoRes createRoom(CreateRoomReq req, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        // Room 생성
        Room room = Room.builder()
                .title(req.title())
                .password(req.password())
                .hostId(userId)
                .totalRounds(req.totalRounds())
                .maxPlayers(req.maxPlayers())
                .build();

        roomRepository.save(room);

        // 방장을 Participant로 등록
        Participant host =
                Participant.builder().userId(user).room(room).isHost(true).build();

        participantRepository.save(host);
        room.getParticipants().add(host);

        broadcastLobbyUpdate();

        return getRoomDetail(room.getId());
    }

    public List<GetRoomListRes> getRoomList() {
        return roomRepository.findAll().stream()
                .filter(room -> room.getParticipants().stream()
                        .anyMatch(p -> !p.isLeft() && !p.getUserId().isAi()))
                .map(room -> {
                    String hostNickname = room.getParticipants().stream()
                            .filter(p -> !p.isLeft())
                            .filter(Participant::isHost)
                            .map(p -> p.getUserId().getNickname())
                            .findFirst()
                            .orElse("Unknown");

                    short activePlayers = (short) room.getParticipants().stream()
                            .filter(p -> !p.isLeft())
                            .count();

                    return GetRoomListRes.builder()
                            .roomId(room.getId())
                            .title(room.getTitle())
                            .curPlayers(activePlayers)
                            .maxPlayers(room.getMaxPlayers())
                            .isPlaying(room.isPlaying())
                            .hostNickname(hostNickname)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public RoomInfoRes getRoomDetail(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        List<RoomInfoRes.ParticipantDto> participantDtos = room.getParticipants().stream()
                .filter(p -> !p.isLeft())
                .map(p -> new RoomInfoRes.ParticipantDto(
                        p.getUserId().getId(),
                        p.getUserId().getNickname(),
                        p.isHost(),
                        p.getUserId().isAi()))
                .toList();

        short activePlayers = (short) participantDtos.size();

        return new RoomInfoRes(
                room.getId(),
                room.getTitle(),
                activePlayers,
                room.getMaxPlayers(),
                room.getTotalRounds(),
                room.getHostId(),
                room.isPlaying(),
                participantDtos);
    }

    @Transactional
    public RoomUpdateResponse joinRoom(Long roomId, Long userId, String inputPassword) {
        int maxRetries = 3; // 최대 3번 시도
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                Room room = roomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

                // 비밀번호 체크 (방에 비밀번호가 걸려있는 경우만)
                if (room.getPassword() != null && !room.getPassword().isEmpty()) {
                    if (!room.getPassword().equals(inputPassword)) {
                        throw new ServiceException("400-4", "비밀번호가 일치하지 않습니다.");
                    }
                }

                if (room.isPlaying()) {
                    throw new ServiceException("400-2", "이미 게임이 시작된 방입니다.");
                }

                if (room.getCurPlayers() >= room.getMaxPlayers()) {
                    throw new ServiceException("400-3", "방 인원이 초과되었습니다.");
                }

                boolean alreadyInRoom = participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, userId);
                if (alreadyInRoom) {
                    throw new ServiceException("400-6", "이미 방에 참여 중입니다.");
                }

                User user = userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

                Participant participant = Participant.builder()
                        .userId(user)
                        .room(room)
                        .isHost(false)
                        .build();

                participantRepository.save(participant);
                room.addParticipant(participant);

                ChatMessageDto enterNotice = ChatMessageDto.builder()
                        .type(ChatMessageDto.MessageType.NOTICE)
                        .roomId(roomId)
                        .sender("System")
                        .message(user.getNickname() + "님이 입장하셨습니다.")
                        .build();
                messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", enterNotice);

                broadcastLobbyUpdate();

                return RoomUpdateResponse.builder()
                        .roomId(roomId)
                        .type("USER_ENTER")
                        .participants(getActiveParticipants(room).stream()
                                .map(p -> p.getUserId().getNickname())
                                .toList())
                        .message(user.getNickname() + "님이 입장하셨습니다.")
                        .build();
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw new ServiceException("409-1", "접속자가 너무 많아 입장에 실패했습니다. 다시 시도해 주세요.");
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new ServiceException("500-0", "알 수 없는 오류가 발생했습니다.");
    }

    @Transactional
    public RoomInfoRes addAiParticipant(Long roomId, Long hostId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        // 방장만 AI 추가 가능
        if (!room.getHostId().equals(hostId)) {
            throw new ServiceException("403-1", "방장만 AI를 추가할 수 있습니다.");
        }

        if (room.isPlaying()) {
            throw new ServiceException("400-2", "이미 게임이 시작된 방입니다.");
        }

        if (room.getCurPlayers() >= room.getMaxPlayers()) {
            throw new ServiceException("400-3", "방 인원이 초과되었습니다.");
        }

        User aiUser =
                userRepository.findByIsAi(true).orElseThrow(() -> new ServiceException("404-1", "AI 유저를 찾을 수 없습니다."));

        boolean alreadyInRoom = participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, aiUser.getId());
        if (alreadyInRoom) {
            throw new ServiceException("400-5", "AI는 이미 방에 참여 중입니다.");
        }

        Participant aiParticipant =
                Participant.builder().userId(aiUser).room(room).isHost(false).build();

        participantRepository.save(aiParticipant);
        room.addParticipant(aiParticipant);

        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService != null) {
            aiChatService.triggerOnAiJoin(roomId, aiUser.getNickname());
        }

        broadcastLobbyUpdate();

        return getRoomDetail(roomId);
    }

    @Transactional
    public RoomUpdateResponse leaveRoom(Long roomId, Long userId) {
        Optional<Participant> participantOpt = participantRepository.findByRoomIdAndUserId_Id(roomId, userId);

        if (participantOpt.isEmpty()) {
            cleanupOrRepairRoom(roomId);
            broadcastLobbyUpdate();
            return null;
        }

        Participant participant = participantOpt.get();

        if (participant.isLeft()) {
            cleanupOrRepairRoom(roomId);
            broadcastLobbyUpdate();
            return null;
        }

        return leaveParticipant(participant);
    }

    @Transactional
    public RoomUpdateResponse leaveCurrentRoom(Long userId) {
        Optional<Participant> participantOpt =
                participantRepository.findFirstByUserId_IdAndIsLeftFalseOrderByIdDesc(userId);

        if (participantOpt.isEmpty()) {
            return null;
        }

        return leaveParticipant(participantOpt.get());
    }

    private boolean cleanupOrRepairRoom(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);

        if (room == null) {
            return true;
        }

        long activeHumanCount = participantRepository.countActiveHumanByRoomId(roomId);

        if (activeHumanCount == 0) {
            deleteRoomWithGameData(room);
            return true;
        }

        List<Participant> activeParticipants = participantRepository.findActiveParticipantsByRoomId(roomId);

        ensureHost(room, activeParticipants);

        return false;
    }

    private RoomUpdateResponse leaveParticipant(Participant participant) {
        Room room = participant.getRoom();
        Long roomId = room.getId();

        User user = participant.getUserId();
        Long leaverId = user.getId();

        boolean playing = room.isPlaying();

        // 1. 먼저 퇴장 처리
        if (playing) {
            // 게임 중에는 RoundParticipant, RoundSubmission FK가 Participant를 참조할 수 있으므로 삭제하지 않는다.
            room.markParticipantLeft(participant);
        } else {
            // 대기 중에는 라운드 관련 FK가 없으므로 Participant 삭제 가능
            room.removeParticipant(participant);
            participantRepository.delete(participant);
        }

        // 2. 퇴장 후 남아 있는 활성 참가자 계산
        List<Participant> activeParticipants = participantRepository.findActiveParticipantsByRoomId(roomId);

        // 3. 남은 인간 유저 수 계산
        long activeHumanCount = participantRepository.countActiveHumanByRoomId(roomId);

        // 4. 인간 유저가 0명이면 방 완전 삭제
        if (activeHumanCount == 0) {
            sendLeaveNotice(roomId, user.getNickname());
            deleteRoomWithGameData(room);
            broadcastLobbyUpdate();
            return null;
        }

        // 5. 인간 유저가 남아 있으면 방장 보정
        HostChangeResult hostChangeResult = ensureHost(room, activeParticipants);

        // 6. 채팅 알림 전송
        if (hostChangeResult.changed()) {
            sendHostChangedNotice(roomId, hostChangeResult.newHostNickname());
        }

        sendLeaveNotice(roomId, user.getNickname());

        // 7. 로비 갱신
        broadcastLobbyUpdate();

        // 8. 남은 참가자들에게 방 상태 변경 이벤트 반환
        return RoomUpdateResponse.builder()
                .roomId(roomId)
                .type("USER_LEAVE")
                .leaverId(leaverId)
                .newHostId(room.getHostId())
                .participants(activeParticipants.stream()
                        .map(p -> p.getUserId().getNickname())
                        .toList())
                .message(user.getNickname() + "님이 퇴장하셨습니다.")
                .build();
    }

    private List<Participant> getActiveParticipants(Room room) {
        return room.getParticipants().stream().filter(p -> !p.isLeft()).toList();
    }

    // 게임 중 방 삭제: round_participant FK 순서대로 제거 후 room cascade 삭제
    private void deleteRoomWithGameData(Room room) {
        Long roomId = room.getId();

        // FK 순서 때문에 제출 -> 라운드 참가자 -> 라운드 순서로 삭제
        roundSubmissionRepository.deleteByRoomId(roomId);
        roundParticipantRepository.deleteByRoomId(roomId);
        roundRepository.deleteByRoomId(roomId);

        // Redis 실시간 랭킹 캐시도 방 삭제 시 함께 제거
        rankingService.clearRanking(roomId);

        roomRepository.delete(room);
    }

    @Transactional
    public RoomInfoRes removeAiParticipant(Long roomId, Long hostId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        if (!room.getHostId().equals(hostId)) {
            throw new ServiceException("403-1", "방장만 AI를 제거할 수 있습니다.");
        }

        if (room.isPlaying()) {
            throw new ServiceException("400-2", "이미 게임이 시작된 방입니다.");
        }

        User aiUser =
                userRepository.findByIsAi(true).orElseThrow(() -> new ServiceException("404-1", "AI 유저를 찾을 수 없습니다."));

        Participant aiParticipant = participantRepository
                .findByRoomIdAndUserId_IdAndIsLeftFalse(roomId, aiUser.getId())
                .orElseThrow(() -> new ServiceException("404-3", "AI가 방에 참여 중이지 않습니다."));

        room.removeParticipant(aiParticipant);
        participantRepository.delete(aiParticipant);

        broadcastLobbyUpdate();

        return getRoomDetail(roomId);
    }

    @Transactional
    public void finishGame(Long roomId) {
        // 방 정보 조회 및 상태 변경
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));
        room.finishGame(); //

        // 랭킹 데이터 가져오기
        var rankingList = rankingService.getRankingList(roomId);

        // 참여자 목록 조회
        List<Participant> participants = participantRepository.findByRoomId(roomId);

        int maxWinCount = participants.stream()
                .mapToInt(Participant::getRoundWinCount)
                .max()
                .orElse(0);

        for (Participant p : participants) {
            if (p.getUserId().isAi()) continue; // AI는 통계 기록 제외

            // 모든 참가자 전체 판수 기록
            p.getUserId().getStats().recordGame();

            // 최고 라운드 승리자들을 최종 우승자로 기록
            if (p.getRoundWinCount() == maxWinCount && maxWinCount > 0) {
                p.markWinner();
                p.getUserId().getStats().recordWin(); // 승리 판수 기록
            }
        }

        rankingService.clearRanking(roomId);
    }

    public List<RankingRes> getFinalRanking(Long roomId) {
        //  Redis에서 정렬된 랭킹 리스트 가져옴
        var redisRanking = rankingService.getRankingList(roomId);

        // Redis에 데이터가 있다면
        if (redisRanking != null && !redisRanking.isEmpty()) {
            return redisRanking.stream()
                    .map(tuple -> {
                        Long userId = Long.valueOf(tuple.getValue()); // Redis에 저장된 userId
                        double score = tuple.getScore(); // Redis에 저장된 점수

                        // 참여자 정보 매핑 (닉네임 등을 위해 필요)
                        return participantRepository
                                .findByRoomIdAndUserId_Id(roomId, userId)
                                .map(p -> RankingRes.builder()
                                        .userId(userId)
                                        .nickname(p.getUserId().getNickname())
                                        .roundWinCount((int) score)
                                        .isWinner(p.isWinner())
                                        .build())
                                .orElse(null);
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        // Redis가 비어있다면 기존 DB 로직을 Fallback으로 사용
        return participantRepository.findByRoomId(roomId).stream()
                .map(p -> RankingRes.builder()
                        .userId(p.getUserId().getId())
                        .nickname(p.getUserId().getNickname())
                        .roundWinCount(p.getRoundWinCount())
                        .isWinner(p.isWinner())
                        .build())
                .sorted((a, b) -> b.roundWinCount() - a.roundWinCount())
                .toList();
    }

    private HostChangeResult ensureHost(Room room, List<Participant> activeParticipants) {
        boolean hasActiveHost = activeParticipants.stream()
                .anyMatch(p -> p.isHost() && !p.getUserId().isAi());

        if (hasActiveHost) {
            return new HostChangeResult(false, room.getHostId(), null);
        }

        Participant nextHost = activeParticipants.stream()
                .filter(p -> !p.getUserId().isAi())
                .findFirst()
                .orElseThrow(() -> new ServiceException("500-2", "방장을 위임할 인간 참가자가 없습니다."));

        nextHost.makeHost();
        room.changeHost(nextHost.getUserId().getId());

        return new HostChangeResult(
                true, nextHost.getUserId().getId(), nextHost.getUserId().getNickname());
    }

    private record HostChangeResult(boolean changed, Long newHostId, String newHostNickname) {}

    private void sendLeaveNotice(Long roomId, String nickname) {
        ChatMessageDto leaveNotice = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.NOTICE)
                .roomId(roomId)
                .sender("System")
                .message(nickname + "님이 퇴장하셨습니다.")
                .build();

        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", leaveNotice);
    }

    private void sendHostChangedNotice(Long roomId, String nextHostNickname) {
        ChatMessageDto hostNotice = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.NOTICE)
                .roomId(roomId)
                .sender("System")
                .message("방장이 " + nextHostNickname + "님으로 변경되었습니다.")
                .build();

        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", hostNotice);
    }

    public void inviteFriend(Long roomId, Long inviterId, Long friendId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        if (!participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, inviterId)) {
            throw new ServiceException("403-2", "방 참여자만 초대할 수 있습니다.");
        }

        if (room.isPlaying()) {
            throw new ServiceException("400-2", "게임 중에는 초대할 수 없습니다.");
        }

        if (room.getCurPlayers() >= room.getMaxPlayers()) {
            throw new ServiceException("400-3", "방 인원이 초과되었습니다.");
        }

        User inviter =
                userRepository.findById(inviterId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));
        User friend =
                userRepository.findById(friendId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        Friendship friendship = friendshipRepository
                .findByUsers(inviter, friend)
                .orElseThrow(() -> new ServiceException("403-3", "친구 관계가 아닙니다."));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ServiceException("403-3", "친구 관계가 아닙니다.");
        }

        if (participantRepository.existsByRoomIdAndUserId_IdAndIsLeftFalse(roomId, friendId)) {
            throw new ServiceException("400-6", "이미 방에 참여 중인 친구입니다.");
        }

        messagingTemplate.convertAndSend(
                "/sub/users/" + friendId + "/notifications",
                new RoomInviteResponse(roomId, room.getTitle(), inviterId, inviter.getNickname()));
    }

    /*
    @Transactional
    public void startGame(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        if (!room.getHostId().equals(userId)) {
            throw new ServiceException("403-2", "방장만 게임을 시작할 수 있습니다.");
        }

        if (room.getCurPlayers() < 2) {
            throw new ServiceException("400-5", "최소 2명의 플레이어가 필요합니다.");
        }

        room.startGame();
    }

     */

}
