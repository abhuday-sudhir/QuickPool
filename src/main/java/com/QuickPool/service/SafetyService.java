package com.QuickPool.service;

import com.QuickPool.dtos.ReportUserDto;
import com.QuickPool.entity.UserBlock;
import com.QuickPool.entity.UserReport;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.UserBlockRepository;
import com.QuickPool.repository.UserReportRepository;
import com.QuickPool.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SafetyService {

    @Autowired
    private UserBlockRepository blockRepository;

    @Autowired
    private UserReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ForbiddenException("You cannot block yourself");
        }
        if (!userRepository.existsById(blockedId)) {
            throw new NotFoundException("User not found");
        }
        if (blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).isPresent()) {
            return; // already blocked; blocking twice is a no-op, not an error
        }
        UserBlock block = new UserBlock();
        block.setBlockerId(blockerId);
        block.setBlockedId(blockedId);
        block.setCreatedAt(LocalDateTime.now());
        blockRepository.save(block);
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockRepository::delete);
    }

    public List<UUID> blockedIds(UUID userId) {
        return blockRepository.findByBlockerIdOrderByCreatedAtDesc(userId).stream()
                .map(UserBlock::getBlockedId)
                .toList();
    }

    /**
     * Everyone hidden from this user — people they blocked *and* people who blocked them.
     * Used to filter search results and to refuse bookings in either direction.
     */
    public Set<UUID> hiddenFrom(UUID userId) {
        return Set.copyOf(blockRepository.findHiddenUserIds(userId));
    }

    public boolean isHidden(UUID viewerId, UUID otherId) {
        return blockRepository.findByBlockerIdAndBlockedId(viewerId, otherId).isPresent()
                || blockRepository.findByBlockerIdAndBlockedId(otherId, viewerId).isPresent();
    }

    @Transactional
    public void report(UUID reporterId, ReportUserDto dto) {
        if (reporterId.equals(dto.getReportedId())) {
            throw new ForbiddenException("You cannot report yourself");
        }
        if (!userRepository.existsById(dto.getReportedId())) {
            throw new NotFoundException("User not found");
        }
        if (reportRepository.existsByReporterIdAndReportedIdAndStatus(
                reporterId, dto.getReportedId(), "OPEN")) {
            throw new ConflictException("You already have an open report about this person");
        }

        UserReport report = new UserReport();
        report.setReporterId(reporterId);
        report.setReportedId(dto.getReportedId());
        report.setRideOfferId(dto.getRideOfferId());
        report.setReason(dto.getReason().trim());
        report.setDetails(dto.getDetails() == null || dto.getDetails().isBlank()
                ? null : dto.getDetails().trim());
        report.setStatus("OPEN");
        report.setCreatedAt(LocalDateTime.now());
        reportRepository.save(report);
    }
}
