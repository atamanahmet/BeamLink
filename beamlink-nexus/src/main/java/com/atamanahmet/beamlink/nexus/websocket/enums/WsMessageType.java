package com.atamanahmet.beamlink.nexus.websocket.enums;

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
     * Backend to UI
     * WIP
     */
    UI_PEER_LIST,
    UI_TRANSFER_PROGRESS,
    UI_DEVICE_STATUS,
    UI_NOTIFICATION;

}