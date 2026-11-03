package com.atamanahmet.beamlink.peer.http;

import com.atamanahmet.beamlink.peer.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Injects device auth headers on every outbound request.
 * Only place in codebase that knows about X-Public-Token and X-Public-Id.
 */
@Primary
@Component
@RequiredArgsConstructor
public class DeviceAuthHttpSender implements HttpSender {

    private final DefaultHttpSender delegate;
    private final DeviceService deviceService;

    @Override
    public HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException {

        UUID publicId = deviceService.getPublicId();
        String publicToken = deviceService.getPublicToken();

        if (publicId == null || publicToken == null) {
            throw new IllegalStateException(
                    "Device not approved yet. publicId or publicToken is null.");
        }

        HttpRequest authenticated = HttpRequest
                .newBuilder(request, (k, v) -> true)
                .header("X-Public-Token", publicToken)
                .header("X-Public-Id", publicId.toString())
                .build();

        return delegate.send(authenticated);
    }

    @Override
    public HttpResponse<String> sendPinned(HttpRequest request, String pin)
            throws IOException, InterruptedException {
        UUID publicId = deviceService.getPublicId();
        String publicToken = deviceService.getPublicToken();
        if (publicId == null || publicToken == null) {
            throw new IllegalStateException(
                    "Device not approved yet. publicId or publicToken is null.");
        }
        HttpRequest authenticated = HttpRequest
                .newBuilder(request, (k, v) -> true)
                .header("X-Public-Token", publicToken)
                .header("X-Public-Id", publicId.toString())
                .build();
        return delegate.sendPinned(authenticated, pin);
    }
}