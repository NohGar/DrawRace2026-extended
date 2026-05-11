package backend.drawrace.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(
                        jwtSchemeName,
                        new SecurityScheme()
                                .name(jwtSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));

        OpenAPI openAPI = new OpenAPI();

        openAPI.setInfo(new Info().title("DrawRace API 명세서").description("""
                    ## 🎨 DrawRace 통합 API 가이드
                    방 관리, 게임 라운드 진행, 실시간 채팅 및 AI 검열 시스템 명세입니다.

                    ### 🔒 인증 (Security)
                    - 모든 API는 **JWT 토큰 인증**이 필요합니다.
                    - 상단의 **Authorize** 버튼을 클릭하여 토큰을 입력하세요.

                    ---
                    ### 💬 실시간 통신 (STOMP/WS)

                    | 기능 | 클라이언트 발행 (SEND) | 서버 구독 (SUBSCRIBE) | 설명 |
                    | :--- | :--- | :--- | :--- |
                    | **채팅** | `/pub/rooms/{roomId}/chat` | `/sub/rooms/{roomId}/chat` | AI 검열 후 메시지 전송 |
                    | **드로잉** | `/pub/rooms/{roomId}/draw` | `/sub/rooms/{roomId}/draw` | 실시간 마우스 좌표 공유 |
                    | **방 상태** | - | `/sub/rooms/{roomId}` | 입장/퇴장/방장 위임 알림 |

                    ---
                    """).version("v1.0.0"));

        openAPI.setComponents(components);
        openAPI.addSecurityItem(securityRequirement);

        return openAPI;
    }
}
