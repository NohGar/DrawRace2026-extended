package backend.drawrace.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해주세요.") @Pattern(regexp = "^[가-힣a-zA-Z0-9]*$", message = "닉네임은 한글, 영문, 숫자만 가능합니다.") String nickname) {}
