package backend.drawrace.domain.room.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RoomServiceLobbyTest {

    @InjectMocks
    private RoomService roomService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("방 생성 시 로비(/sub/lobby)에 최신 목록이 브로드캐스트 되어야 한다")
    void createRoomLobbyUpdateTest() {
        Long userId = 1L;
        User user = User.builder().nickname("호스트").build();
        ReflectionTestUtils.setField(user, "id", userId);

        CreateRoomReq req = new CreateRoomReq("테스트방", (short) 4, (short) 3, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1L); // ID 세팅
            return r;
        });

        when(roomRepository.findById(anyLong())).thenAnswer(i -> {
            Room r = Room.builder().title("테스트방").build();
            ReflectionTestUtils.setField(r, "id", i.getArgument(0));
            return Optional.of(r);
        });

        roomService.createRoom(req, userId);

        verify(messagingTemplate).convertAndSend(eq("/sub/lobby"), anyList());
    }
}
