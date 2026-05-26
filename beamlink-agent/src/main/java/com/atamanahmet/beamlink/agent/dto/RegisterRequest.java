package com.atamanahmet.beamlink.agent.dto;

public record RegisterRequest(
        String username,
        String password
) {}