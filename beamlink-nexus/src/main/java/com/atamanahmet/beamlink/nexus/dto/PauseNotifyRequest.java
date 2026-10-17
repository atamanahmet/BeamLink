package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
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
