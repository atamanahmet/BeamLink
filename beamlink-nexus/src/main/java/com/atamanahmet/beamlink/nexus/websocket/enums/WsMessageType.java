package com.atamanahmet.beamlink.nexus.websocket.enums;

public enum WsMessageType {

    /**
     * Nexus to Agent
     */
    APPROVAL_PUSH,
    PEER_LIST_UPDATE,
    AGENT_JOINED,
    AGENT_OFFLINE,
    RENAME_PUSH,

    /**
     * Agent to Nexus
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
    UI_AGENT_STATUS,
    UI_NOTIFICATION;

}