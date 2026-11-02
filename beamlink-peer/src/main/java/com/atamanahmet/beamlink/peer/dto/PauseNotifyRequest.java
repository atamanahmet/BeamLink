package com.atamanahmet.beamlink.peer.dto;

import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
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
    private String reason;
}
