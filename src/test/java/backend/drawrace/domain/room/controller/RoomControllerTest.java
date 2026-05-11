package backend.drawrace.domain.room.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.global.security.SecurityUser;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        // 가짜 SecurityUser 생성 (프로젝트의 SecurityUser 구조에 맞게 생성자 호출)
        SecurityUser mockSecurityUser = new SecurityUser(1L, "test@test.com");
        // 시큐리티 컨텍스트에 인증 정보 강제 주입
        Authentication auth =
                new UsernamePasswordAuthenticationToken(mockSecurityUser, null, mockSecurityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @WithMockUser
    @DisplayName("방 생성 API - 성공 시 201-1 코드를 반환한다")
    void createRoom_api_success() throws Exception {
        // given
        RoomInfoRes res = new RoomInfoRes(1L, "방제목", (short) 1, (short) 4, (short) 3, 1L, false, List.of());
        given(roomService.createRoom(any(), anyLong())).willReturn(res);

        // when & then
        mockMvc.perform(post("/api/rooms")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"방제목\", \"maxPlayers\":4, \"totalRounds\":3}")) // CreateRoomReq 필드 개수 맞춤
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.title").value("방제목"));
    }

    @Test
    @WithMockUser
    @DisplayName("방 상세 조회 API - 성공 시 200-2 코드를 반환한다")
    void getRoomDetail_api_success() throws Exception {
        // given
        RoomInfoRes res = new RoomInfoRes(1L, "상세방", (short) 1, (short) 4, (short) 3, 1L, false, List.of());
        given(roomService.getRoomDetail(1L)).willReturn(res);

        // when & then
        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-2"))
                .andExpect(jsonPath("$.msg").value("방 상세 정보를 성공적으로 조회했습니다."));
    }
}
