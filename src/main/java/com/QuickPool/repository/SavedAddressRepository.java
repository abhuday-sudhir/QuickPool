package com.QuickPool.repository;

import com.QuickPool.entity.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedAddressRepository extends JpaRepository<SavedAddress, UUID> {

    List<SavedAddress> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<SavedAddress> findByUserIdAndLabelIgnoreCase(UUID userId, String label);

    long countByUserId(UUID userId);
}
