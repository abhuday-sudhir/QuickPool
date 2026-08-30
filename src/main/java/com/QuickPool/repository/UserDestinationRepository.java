package com.QuickPool.repository;

import com.QuickPool.entity.UserDestination;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDestinationRepository extends JpaRepository<UserDestination, UUID> {

    Optional<UserDestination> findByUserIdAndGeoKey(UUID userId, String geoKey);

    List<UserDestination> findByUserIdOrderByUseCountDescLastUsedAtDesc(UUID userId, Pageable pageable);

    List<UserDestination> findByUserIdOrderByLastUsedAtDesc(UUID userId, Pageable pageable);
}
