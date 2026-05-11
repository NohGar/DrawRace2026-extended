package backend.drawrace.domain.round.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDrawingRequest {

    @NotNull private Long participantId;

    @NotBlank private String imageData;
}
