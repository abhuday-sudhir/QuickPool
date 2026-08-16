package com.QuickPool.repository;

import com.QuickPool.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User , UUID> {
    Optional<User> findByPhone(String phone);
}
