package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import com.atamanahmet.beamlink.peer.exception.TransferProtocolException;
import com.atamanahmet.beamlink.peer.net.LanIpv4Addresses;
import com.atamanahmet.beamlink.peer.service.DiskReserveProvider;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import com.atamanahmet.beamlink.peer.service.SpeedLimitSettingsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final SpeedLimitSettingsCache speedLimitSettingsCache;
    private final DiskReserveProvider diskReserveProvider;
    private final DeviceConfig deviceConfig;

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

    /**
     * LAN IPv4 list and TLS port for the UI copy lines.
     */
    @GetMapping("/lan-addresses")
    public ResponseEntity<Map<String, Object>> getLanAddresses() {
        List<String> addresses = LanIpv4Addresses.list();
        return ResponseEntity.ok(Map.of(
                "addresses", addresses,
                "tlsPort", deviceConfig.getTlsPort()));
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
