package com.QuickPool.repository;

import com.QuickPool.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByUserId(UUID userId);
    List<Vehicle> findByUserIdIn(java.util.Collection<UUID> userIds);
}
