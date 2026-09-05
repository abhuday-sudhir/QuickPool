package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * A lean page envelope for lists that grow without bound (notifications, a driver's ride
 * history, booking requests). Deliberately not Spring's {@code Page<T>} — that serializes
 * pageable/sort metadata the client never uses and, worse, backs onto a {@code Page} query
 * that runs an extra {@code COUNT(*)} every call. This wraps a {@code Slice} instead: one
 * query, one extra row fetched to know whether there's more.
 */
@Getter
@AllArgsConstructor
public class PageResponseDto<T> {
    private final List<T> content;
    private final boolean hasNext;
}
