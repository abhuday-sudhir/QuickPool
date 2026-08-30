package com.QuickPool.repository;

import com.QuickPool.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /** Ids the user should never see, in either direction. */
    @Query("select case when b.blockerId = :userId then b.blockedId else b.blockerId end "
            + "from UserBlock b where b.blockerId = :userId or b.blockedId = :userId")
    List<UUID> findHiddenUserIds(@Param("userId") UUID userId);
}
