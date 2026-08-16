package com.QuickPool.repository;

import com.QuickPool.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}