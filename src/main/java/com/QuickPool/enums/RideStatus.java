package com.QuickPool.enums;

public enum RideStatus {
    ACTIVE,
    /** Departure came and went without the driver ever starting it. */
    EXPIRED,
    IN_PROGRESS,
    FULL,
    CANCELLED,
    COMPLETED
}
