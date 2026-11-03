package com.atamanahmet.beamlink.peer.http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin wrapper for HttpClient so services and tests don't depend on the JDK class
 */
public interface HttpSender {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

    /**
     * HTTPS to a peer with a listed pin. Never used for http.
     */
    HttpResponse<String> sendPinned(HttpRequest request, String pin)
            throws IOException, InterruptedException;
}