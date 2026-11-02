package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.ResumePolicyMode;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.service.DiskReserveProvider;
import com.atamanahmet.beamlink.nexus.service.NexusResumePolicyStore;
import com.atamanahmet.beamlink.nexus.service.SettingsService;
import com.atamanahmet.beamlink.nexus.service.SpeedLimitSettingsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nexus/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final SpeedLimitSettingsCache speedLimitSettingsCache;
    private final NexusResumePolicyStore resumePolicyStore;
    private final DiskReserveProvider diskReserveProvider;

    @GetMapping("/speed-cap")
    public ResponseEntity<Map<String, Object>> getSpeedCap() {
        return ResponseEntity.ok(limitBody(speedLimitSettingsCache.getFileDefaultMbps()));
    }

    @PutMapping("/speed-cap")
    public ResponseEntity<Map<String, Object>> setSpeedCap(
            @RequestBody(required = false) Map<String, Double> body) {
        Double value = readLimit(body);
        validateLimit(value);
        if (value == null) {
            settingsService.clear(SettingKey.TRANSFER_SPEED_CAP_MBPS);
            speedLimitSettingsCache.setFileDefaultMbps(null);
        } else {
            settingsService.set(SettingKey.TRANSFER_SPEED_CAP_MBPS, value);
            speedLimitSettingsCache.setFileDefaultMbps(value);
        }
        return ResponseEntity.ok(limitBody(speedLimitSettingsCache.getFileDefaultMbps()));
    }

    @GetMapping("/total-speed-limit")
    public ResponseEntity<Map<String, Object>> getTotalSpeedLimit() {
        return ResponseEntity.ok(limitBody(speedLimitSettingsCache.getTotalMbps()));
    }

    @PutMapping("/total-speed-limit")
    public ResponseEntity<Map<String, Object>> setTotalSpeedLimit(
            @RequestBody(required = false) Map<String, Double> body) {
        Double value = readLimit(body);
        validateLimit(value);
        if (value == null) {
            settingsService.clear(SettingKey.TOTAL_SPEED_LIMIT_MBPS);
            speedLimitSettingsCache.setTotalMbps(null);
        } else {
            settingsService.set(SettingKey.TOTAL_SPEED_LIMIT_MBPS, value);
            speedLimitSettingsCache.setTotalMbps(value);
        }
        return ResponseEntity.ok(limitBody(speedLimitSettingsCache.getTotalMbps()));
    }

    @GetMapping("/resume-policy")
    public ResponseEntity<Map<String, String>> getResumePolicy() {
        return ResponseEntity.ok(Map.of("resumePolicy", resumePolicyStore.getMode().name()));
    }

    @PutMapping("/resume-policy")
    public ResponseEntity<Map<String, Object>> setResumePolicy(@RequestBody Map<String, String> body) {
        String raw = body == null ? null : body.get("resumePolicy");
        if (raw == null || raw.isBlank()) {
            throw new TransferProtocolException(
                    "INVALID_RESUME_POLICY",
                    "resumePolicy must be OWNER_ONLY or ANYONE",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            ResumePolicyMode mode = ResumePolicyMode.valueOf(raw.trim().toUpperCase());
            resumePolicyStore.setMode(mode);
            return ResponseEntity.ok(Map.of("resumePolicy", mode.name()));
        } catch (IllegalArgumentException e) {
            throw new TransferProtocolException(
                    "INVALID_RESUME_POLICY",
                    "resumePolicy must be OWNER_ONLY or ANYONE",
                    HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/min-free-space")
    public ResponseEntity<Map<String, Object>> getMinFreeSpace() {
        return ResponseEntity.ok(Map.of("value", diskReserveProvider.getBytes()));
    }

    @PutMapping("/min-free-space")
    public ResponseEntity<Map<String, Object>> setMinFreeSpace(
            @RequestBody(required = false) Map<String, Object> body) {
        long value = readLong(body);
        settingsService.set(SettingKey.MIN_FREE_SPACE_BYTES, Long.toString(value));
        diskReserveProvider.refresh();
        return ResponseEntity.ok(Map.of("value", diskReserveProvider.getBytes()));
    }

    /**
     * Reads a Long JSON number named value.
     */
    private static long readLong(Map<String, Object> body) {
        if (body == null || !body.containsKey("value") || body.get("value") == null) {
            throw new InvalidRequestException("value is required", "INVALID_SETTING");
        }
        Object raw = body.get("value");
        if (raw instanceof Number n) {
            long parsed = n.longValue();
            SettingKey.MIN_FREE_SPACE_BYTES.validate(Long.toString(parsed));
            return parsed;
        }
        SettingKey.MIN_FREE_SPACE_BYTES.validate(String.valueOf(raw));
        return Long.parseLong(String.valueOf(raw).trim());
    }

    private static Double readLimit(Map<String, Double> body) {
        if (body == null) {
            return null;
        }
        if (body.containsKey("limitMbps")) {
            return body.get("limitMbps");
        }
        return body.get("value");
    }

    private static void validateLimit(Double value) {
        if (value != null && value <= 0) {
            throw new TransferProtocolException(
                    "INVALID_SPEED_LIMIT",
                    "Speed limit must be null or a positive Mb/s value",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static Map<String, Object> limitBody(Double mbps) {
        Map<String, Object> body = new HashMap<>();
        body.put("limitMbps", mbps);
        body.put("value", mbps);
        return body;
    }
}
