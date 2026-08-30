package com.QuickPool.enums;

public enum BookingStatus {
    /** Passenger requested a seat; seat is held while the driver decides. */
    PENDING,
    /** Driver accepted the request. */
    CONFIRMED,
    /** Driver declined the request. */
    REJECTED,
    /** Passenger withdrew, or the driver cancelled the whole ride. */
    CANCELLED,
    COMPLETED
}
