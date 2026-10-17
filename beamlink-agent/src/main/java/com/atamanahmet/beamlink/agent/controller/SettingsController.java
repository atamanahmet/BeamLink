package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import com.atamanahmet.beamlink.agent.exception.TransferProtocolException;
import com.atamanahmet.beamlink.agent.service.SettingsService;
import com.atamanahmet.beamlink.agent.service.SpeedLimitSettingsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final SpeedLimitSettingsCache speedLimitSettingsCache;

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
