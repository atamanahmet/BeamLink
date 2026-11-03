package com.atamanahmet.beamlink.peer.websocket.enums;

public enum WsMessageType {

    /**
     * Nexus to Device
     */
    PEER_LIST_UPDATE,
    DEVICE_JOINED,
    DEVICE_OFFLINE,
    RENAME_PUSH,

    /**
     * Device to Nexus
     */
    HEARTBEAT,
    LOG_SYNC,
    STATUS_UPDATE,

    /**
     * Device to Device
     */
    PING,
    PONG;

}