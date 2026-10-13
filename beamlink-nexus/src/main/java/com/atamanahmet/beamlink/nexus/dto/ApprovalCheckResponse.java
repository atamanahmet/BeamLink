package com.atamanahmet.beamlink.nexus.dto;

import lombok.Data;

/**
 * Response for approval check
 */
@Data
public class ApprovalCheckResponse {
    private String status;
    private String authToken;
    private String publicToken;
}
