package com.atamanahmet.beamlink.agent.http;

import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Injects agent auth headers on every outbound request.
 * Only place in codebase that knows about X-Public-Token and X-Public-Id.
 */
@Primary
@Component
@RequiredArgsConstructor
public class AgentAuthHttpSender implements HttpSender {

    private final DefaultHttpSender delegate;
    private final AgentService agentService;

    @Override
    public HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException {

        HttpRequest authenticated = HttpRequest
                .newBuilder(request, (k, v) -> true)
                .header("X-Public-Token", agentService.getPublicToken())
                .header("X-Public-Id", agentService.getPublicId().toString())
                .build();

        return delegate.send(authenticated);
    }
}