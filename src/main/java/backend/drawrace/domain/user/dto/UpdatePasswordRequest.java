package backend.drawrace.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UpdatePasswordRequest {

    @NotBlank(message = "현재 비밀번호는 필수입니다.") private String currentPassword;

    @NotBlank(message = "새 비밀번호는 필수입니다.") @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.") private String newPassword;
}
