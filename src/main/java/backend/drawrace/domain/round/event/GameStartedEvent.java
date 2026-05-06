package backend.drawrace.domain.round.event;

import backend.drawrace.domain.round.dto.RoundStartResponse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GameStartedEvent {

    private final Long roomId;
    private final RoundStartResponse roundStartResponse;
}
