package backend.drawrace.domain.round.validator;

import java.util.Optional;

import org.springframework.stereotype.Component;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.global.exception.ServiceException;

@Component
public class RoundValidator {

    public void validateStartGame(Room room, long participantCount, Optional<Round> activeRound, Long userId) {
        validateIsHost(room, userId);
        validateRoomNotPlaying(room);
        validateParticipantCount(participantCount);
        validateNoActiveRound(activeRound);
    }

    public void validateRoundInProgress(Round round) {
        if (round.getStatus() != RoundStatus.IN_PROGRESS) {
            throw new ServiceException("400-1", "진행 중인 라운드가 아닙니다.");
        }
    }

    public void validateRoundParticipant(boolean canPlay) {
        if (!canPlay) {
            throw new ServiceException("403-1", "이번 라운드 참가 대상이 아닙니다.");
        }
    }

    public void validateNotSubmitted(boolean alreadySubmitted) {
        if (alreadySubmitted) {
            throw new ServiceException("400-2", "이미 제출을 완료한 참가자입니다.");
        }
    }

    private void validateIsHost(Room room, Long userId) {
        if (!room.getHostId().equals(userId)) {
            throw new ServiceException("403-2", "방장만 게임을 시작할 수 있습니다.");
        }
    }

    private void validateRoomNotPlaying(Room room) {
        if (room.isPlaying()) {
            throw new ServiceException("400-3", "이미 게임이 진행 중인 방입니다.");
        }
    }

    private void validateParticipantCount(long participantCount) {
        if (participantCount < 2) {
            throw new ServiceException("400-4", "게임 시작은 최소 2명 이상부터 가능합니다.");
        }
    }

    private void validateNoActiveRound(Optional<Round> activeRound) {
        if (activeRound.isPresent()) {
            throw new ServiceException("400-5", "이미 진행 중인 라운드가 존재합니다.");
        }
    }

    public void validateParticipantOwner(Participant participant, Long userId) {
        if (!participant.getUserId().getId().equals(userId)) {
            throw new ServiceException("403-3", "본인 참가 정보로만 제출할 수 있습니다.");
        }
    }
}
