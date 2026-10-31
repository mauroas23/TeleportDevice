package com.teleportdevice.network;

/** Acciones que el cliente puede SOLICITAR. El servidor decide si se ejecutan. */
public enum ActionType {
    REQUEST_SYNC, SAVE, DELETE, RENAME, TELEPORT
}
