package com.atamanahmet.beamlink.peer.security;

import java.util.UUID;

/**
 * Authenticated peer caller from a public JWT.
 */
public record PeerPrincipal(String publicId, UUID deviceId) {
}
