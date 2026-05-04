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
import backend.drawrace.domain.room.dto.response.RoomUpdateResponse;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
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
    private final RankingService rankingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    @Transactional
    public RoomInfoRes createRoom(CreateRoomReq req, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        // Room 생성
        Room room = Room.builder()
                .title(req.title())
                .password(req.password())
                .hostId(userId)
                .totalRounds(req.totalRounds())
                .maxPlayers((short) 4)
                .build();

        roomRepository.save(room);

        // 방장을 Participant로 등록
        Participant host =
                Participant.builder().userId(user).room(room).isHost(true).build();

        participantRepository.save(host);
        room.getParticipants().add(host);

        return getRoomDetail(room.getId());
    }

    public List<GetRoomListRes> getRoomList() {
        return roomRepository.findAll().stream()
                .map(room -> {
                    String hostNickname = room.getParticipants().stream()
                            .filter(Participant::isHost)
                            .map(p -> p.getUserId().getNickname())
                            .findFirst()
                            .orElse("Unknown");

                    return GetRoomListRes.builder()
                            .roomId(room.getId())
                            .title(room.getTitle())
                            .curPlayers(room.getCurPlayers())
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
                .map(p -> new RoomInfoRes.ParticipantDto(
                        p.getUserId().getId(),
                        p.getUserId().getNickname(),
                        p.isHost(),
                        p.getUserId().isAi()))
                .toList();

        return new RoomInfoRes(
                room.getId(),
                room.getTitle(),
                room.getCurPlayers(),
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

                return RoomUpdateResponse.builder()
                        .roomId(roomId)
                        .type("USER_ENTER")
                        .participants(room.getParticipants().stream()
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

        boolean alreadyInRoom = participantRepository.existsByRoomIdAndUserId_Id(roomId, aiUser.getId());
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

        return getRoomDetail(roomId);
    }

    @Transactional
    public RoomUpdateResponse leaveRoom(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        Participant participant = participantRepository
                .findByUserId(user)
                .orElseThrow(() -> new ServiceException("404-3", "참여 정보를 찾을 수 없습니다."));

        Room room = participant.getRoom();
        Long roomId = room.getId();
        Long leaverId = userId;
        Long newHostId = null;
        String nextHostNickname = "";

        // 방장이 나가는 경우 방장 위임 로직 (AI는 방장이 될 수 없음)
        if (participant.isHost() && room.getCurPlayers() > 1) {
            Optional<Participant> nextHostOpt = room.getParticipants().stream()
                    .filter(p -> !p.equals(participant) && !p.getUserId().isAi())
                    .findFirst();

            if (nextHostOpt.isEmpty()) {
                // 남은 참여자가 AI뿐 → 방 삭제
                room.removeParticipant(participant);
                participantRepository.delete(participant);
                roomRepository.delete(room);
                return null;
            }

            Participant nextHost = nextHostOpt.get();
            nextHost.makeHost();
            room.changeHost(nextHost.getUserId().getId());

            newHostId = nextHost.getUserId().getId();
            nextHostNickname = nextHost.getUserId().getNickname();
        }

        if (newHostId != null) {
            ChatMessageDto hostNotice = ChatMessageDto.builder()
                    .type(ChatMessageDto.MessageType.NOTICE)
                    .roomId(roomId)
                    .sender("System")
                    .message("방장이 " + nextHostNickname + "님으로 변경되었습니다.")
                    .build();
            messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", hostNotice);
        }

        ChatMessageDto leaveNotice = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.NOTICE)
                .roomId(roomId)
                .sender("System")
                .message(user.getNickname() + "님이 퇴장하셨습니다.")
                .build();
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", leaveNotice);

        // 참여자 제거 및 인원 감소
        room.removeParticipant(participant);
        participantRepository.delete(participant);

        // 방에 아무도 없거나 AI만 남으면 방 삭제
        boolean onlyAiOrEmpty =
                room.getParticipants().stream().allMatch(p -> p.getUserId().isAi());
        if (room.getCurPlayers() == 0 || onlyAiOrEmpty) {
            roomRepository.delete(room);
            return null;
        }
        // 웹소켓 동기화를 위한 데이터 반환
        return RoomUpdateResponse.builder()
                .roomId(roomId)
                .type("USER_LEAVE")
                .leaverId(leaverId)
                .newHostId(newHostId != null ? newHostId : room.getHostId())
                .participants(room.getParticipants().stream()
                        .map(p -> p.getUserId().getNickname())
                        .toList())
                .message(user.getNickname() + "님이 퇴장하셨습니다.")
                .build();
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
                .findByRoomIdAndUserId_Id(roomId, aiUser.getId())
                .orElseThrow(() -> new ServiceException("404-3", "AI가 방에 참여 중이지 않습니다."));

        room.removeParticipant(aiParticipant);
        participantRepository.delete(aiParticipant);

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
        // 해당 방의 모든 참여자 조회
        List<Participant> participants = participantRepository.findByRoomId(roomId);

        return participants.stream()
                .map(p -> RankingRes.builder()
                        .userId(p.getUserId().getId())
                        .nickname(p.getUserId().getNickname())
                        .roundWinCount(p.getRoundWinCount())
                        .isWinner(p.isWinner())
                        .build())
                .sorted((a, b) -> b.roundWinCount() - a.roundWinCount()) // 승리 횟수 내림차순 정렬
                .toList();
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
