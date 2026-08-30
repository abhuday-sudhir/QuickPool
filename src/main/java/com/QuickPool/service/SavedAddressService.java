package com.QuickPool.service;

import com.QuickPool.config.CacheConfig;
import com.QuickPool.dtos.SaveAddressDto;
import com.QuickPool.dtos.SavedAddressDto;
import com.QuickPool.entity.SavedAddress;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.SavedAddressRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SavedAddressService {

    private static final int MAX_PER_USER = 12;

    @Autowired
    private SavedAddressRepository repository;

    @Cacheable(value = CacheConfig.SAVED_ADDRESSES, key = "#userId.toString()")
    public List<SavedAddressDto> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> new SavedAddressDto(
                        a.getId().toString(), a.getLabel(), a.getName(), a.getLat(), a.getLng()))
                .collect(Collectors.toList());
    }

    /** Saving the same label again overwrites it, so "Home" can be moved. */
    @Transactional
    @CacheEvict(value = CacheConfig.SAVED_ADDRESSES, key = "#userId.toString()")
    public SavedAddressDto save(UUID userId, SaveAddressDto dto) {
        String label = dto.getLabel().trim();
        LocalDateTime now = LocalDateTime.now();

        SavedAddress address = repository.findByUserIdAndLabelIgnoreCase(userId, label)
                .orElseGet(() -> {
                    if (repository.countByUserId(userId) >= MAX_PER_USER) {
                        throw new ConflictException("You can save up to " + MAX_PER_USER + " addresses");
                    }
                    SavedAddress fresh = new SavedAddress();
                    fresh.setUserId(userId);
                    fresh.setCreatedAt(now);
                    return fresh;
                });

        address.setLabel(label);
        address.setName(dto.getName().trim());
        address.setLat(dto.getLat());
        address.setLng(dto.getLng());
        address.setUpdatedAt(now);

        SavedAddress saved = repository.save(address);
        return new SavedAddressDto(
                saved.getId().toString(), saved.getLabel(), saved.getName(), saved.getLat(), saved.getLng());
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SAVED_ADDRESSES, key = "#userId.toString()")
    public void delete(UUID userId, UUID addressId) {
        SavedAddress address = repository.findById(addressId)
                .orElseThrow(() -> new NotFoundException("Address not found"));
        if (!address.getUserId().equals(userId)) {
            throw new ForbiddenException("Not your address");
        }
        repository.delete(address);
    }
}
