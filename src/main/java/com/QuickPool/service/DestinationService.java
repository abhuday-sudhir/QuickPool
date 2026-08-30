package com.QuickPool.service;

import com.QuickPool.config.CacheConfig;
import com.QuickPool.dtos.DestinationDto;
import com.QuickPool.entity.UserDestination;
import com.QuickPool.repository.UserDestinationRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DestinationService {

    private static final int MAX_RETURNED = 6;

    @Autowired
    private UserDestinationRepository repository;

    /**
     * Top destinations for a user, cached in Redis. Read-through: a miss (or a Redis
     * outage, via CacheConfig's error handler) simply hits Postgres.
     */
    @Cacheable(value = CacheConfig.FREQUENT_DESTINATIONS, key = "#userId.toString()")
    public List<DestinationDto> getFrequent(UUID userId) {
        log.debug("Cache miss for frequent destinations of {}", userId);
        return repository
                .findByUserIdOrderByUseCountDescLastUsedAtDesc(userId, PageRequest.of(0, MAX_RETURNED))
                .stream()
                .map(d -> new DestinationDto(d.getName(), d.getLat(), d.getLng(), d.getUseCount()))
                .collect(Collectors.toList());
    }

    /** Most recently used destinations, newest first. */
    @Cacheable(value = CacheConfig.RECENT_DESTINATIONS, key = "#userId.toString()")
    public List<DestinationDto> getRecent(UUID userId) {
        return repository
                .findByUserIdOrderByLastUsedAtDesc(userId, PageRequest.of(0, MAX_RETURNED))
                .stream()
                .map(d -> new DestinationDto(d.getName(), d.getLat(), d.getLng(), d.getUseCount()))
                .collect(Collectors.toList());
    }

    /** Record a destination the user actually chose, bumping its counter if already known. */
    @Transactional
    @CacheEvict(value = {CacheConfig.FREQUENT_DESTINATIONS, CacheConfig.RECENT_DESTINATIONS},
            key = "#userId.toString()")
    public void record(UUID userId, String name, double lat, double lng) {
        String geoKey = geoKey(lat, lng);
        LocalDateTime now = LocalDateTime.now();

        var existing = repository.findByUserIdAndGeoKey(userId, geoKey);
        if (existing.isPresent()) {
            UserDestination d = existing.get();
            d.setUseCount(d.getUseCount() + 1);
            d.setLastUsedAt(now);
            // Keep the most recent human-readable label for the same coordinates.
            if (name != null && !name.isBlank()) {
                d.setName(name);
            }
            repository.save(d);
            return;
        }

        UserDestination d = new UserDestination();
        d.setUserId(userId);
        d.setName(name);
        d.setLat(lat);
        d.setLng(lng);
        d.setGeoKey(geoKey);
        d.setUseCount(1);
        d.setLastUsedAt(now);
        d.setCreatedAt(now);
        try {
            repository.save(d);
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the same place first; bump that row instead.
            repository.findByUserIdAndGeoKey(userId, geoKey).ifPresent(other -> {
                other.setUseCount(other.getUseCount() + 1);
                other.setLastUsedAt(now);
                repository.save(other);
            });
        }
    }

    /** ~11m resolution, so the same place picked twice collapses to one row. */
    private static String geoKey(double lat, double lng) {
        return String.format(Locale.ROOT, "%.4f,%.4f", lat, lng);
    }
}
