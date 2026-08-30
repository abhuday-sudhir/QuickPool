package com.QuickPool.repository;

import com.QuickPool.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
    boolean existsByReporterIdAndReportedIdAndStatus(UUID reporterId, UUID reportedId, String status);
}
