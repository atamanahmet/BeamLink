package com.atamanahmet.beamlink.peer.config;

import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class NexusClientConfig {

    /**
     * HTTP client for Nexus REST. Keeps the 10s read and response timeout.
     */
    @Bean("nexusHttpClient")
    public HttpClient nexusHttpClient(NexusTlsPin pin) throws SSLException {
        return pinnedClient(pin)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS)));
    }

    /**
     * HTTP client for Nexus WebSocket. Same pin TLS, no read or response timeout.
     */
    @Bean("nexusWsHttpClient")
    public HttpClient nexusWsHttpClient(NexusTlsPin pin) throws SSLException {
        return pinnedClient(pin);
    }

    /**
     * WebClient on the timed HTTP client.
     */
    @Bean
    public WebClient nexusWebClient(@Qualifier("nexusHttpClient") HttpClient nexusHttpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(nexusHttpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Shared pin TLS and 5s connect timeout for both Nexus clients.
     */
    private HttpClient pinnedClient(NexusTlsPin pin) throws SSLException {
        SslContext ssl = SslContextBuilder.forClient()
                .trustManager(pin)
                .build();
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .secure(spec -> spec.sslContext(ssl));
    }
}
