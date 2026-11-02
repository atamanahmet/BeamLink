package com.atamanahmet.beamlink.nexus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentErrorBody(String error, Integer interval) {

    public static EnrollmentErrorBody of(String error) {
        return new EnrollmentErrorBody(error, null);
    }

    public static EnrollmentErrorBody of(String error, int interval) {
        return new EnrollmentErrorBody(error, interval);
    }
}
