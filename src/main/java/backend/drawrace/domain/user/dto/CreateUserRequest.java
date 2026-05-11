package backend.drawrace.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "이메일은 필수 입력 값입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,

        @NotBlank(message = "비밀번호는 필수 입력 값입니다.") @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.") String password,

        @NotBlank(message = "닉네임은 필수 입력 값입니다.") @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해주세요.") @Pattern(regexp = "^[가-힣a-zA-Z0-9]*$", message = "닉네임은 한글, 영문, 숫자만 가능합니다.") String nickname) {}
