package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for peer pause-notify.
 */
@Getter
@Setter
@NoArgsConstructor
public class PauseNotifyRequest {
    private PauseOwner pausedBy;
}
