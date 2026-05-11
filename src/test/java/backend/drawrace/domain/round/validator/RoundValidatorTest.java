package backend.drawrace.domain.round.validator;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.global.exception.ServiceException;

class RoundValidatorTest {

    private final RoundValidator roundValidator = new RoundValidator();

    @Test
    @DisplayName("게임 시작 검증 성공")
    void validateStartGame_success() throws Exception {
        Room room = createRoom(1L, false, 1L);

        assertThatCode(() -> roundValidator.validateStartGame(room, 2L, Optional.empty(), 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("방장이 아닌 유저가 게임 시작을 요청하면 예외 발생")
    void validateStartGame_notHost() throws Exception {
        Room room = createRoom(1L, false, 1L);

        assertThatThrownBy(() -> roundValidator.validateStartGame(room, 2L, Optional.empty(), 2L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("방장만 게임을 시작할 수 있습니다");
    }

    @Test
    @DisplayName("이미 게임 중인 방이면 예외")
    void validateStartGame_roomAlreadyPlaying() throws Exception {
        Room room = createRoom(1L, true, 1L);

        assertThatThrownBy(() -> roundValidator.validateStartGame(room, 2L, Optional.empty(), 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이미 게임이 진행 중인 방입니다");
    }

    @Test
    @DisplayName("참가자 수 부족이면 예외")
    void validateStartGame_notEnoughParticipants() throws Exception {
        Room room = createRoom(1L, false, 1L);

        assertThatThrownBy(() -> roundValidator.validateStartGame(room, 1L, Optional.empty(), 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("최소 2명 이상");
    }

    @Test
    @DisplayName("활성 라운드가 있으면 예외")
    void validateStartGame_activeRoundExists() throws Exception {
        Room room = createRoom(1L, false, 1L);
        Round round = Round.create(room, 1, "사과");
        setField(round, "id", 99L);

        assertThatThrownBy(() -> roundValidator.validateStartGame(room, 2L, Optional.of(round), 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이미 진행 중인 라운드가 존재합니다");
    }

    @Test
    @DisplayName("진행 중인 라운드가 아니면 예외")
    void validateRoundInProgress_fail() throws Exception {
        Room room = createRoom(1L, true, 1L);
        Round round = Round.create(room, 1, "사과");

        assertThatThrownBy(() -> roundValidator.validateRoundInProgress(round))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("진행 중인 라운드가 아닙니다");
    }

    @Test
    @DisplayName("이번 라운드 참가 대상이 아니면 예외")
    void validateRoundParticipant_fail() {
        assertThatThrownBy(() -> roundValidator.validateRoundParticipant(false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이번 라운드 참가 대상이 아닙니다");
    }

    @Test
    @DisplayName("이미 제출했으면 예외")
    void validateNotSubmitted_fail() {
        assertThatThrownBy(() -> roundValidator.validateNotSubmitted(true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이미 제출을 완료한 참가자입니다");
    }

    private Room createRoom(Long id, boolean isPlaying, Long hostId) throws Exception {
        Room room = Room.builder()
                .title("테스트 방")
                .hostId(hostId)
                .totalRounds((short) 3)
                .maxPlayers((short) 4)
                .curPlayers((short) 2)
                .isPlaying(isPlaying)
                .build();

        setField(room, "id", id);
        return room;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
