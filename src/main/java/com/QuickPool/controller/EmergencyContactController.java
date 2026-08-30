package com.QuickPool.controller;

import com.QuickPool.dtos.EmergencyContactDto;
import com.QuickPool.entity.EmergencyContact;
import com.QuickPool.repository.EmergencyContactRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/emergency-contact")
public class EmergencyContactController {

    @Autowired
    private EmergencyContactRepository repository;

    @GetMapping
    public EmergencyContactDto get(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return repository.findByUserId(userId)
                .map(c -> new EmergencyContactDto(c.getName(), c.getPhone()))
                .orElse(null);
    }

    @PutMapping
    @Transactional
    public EmergencyContactDto save(@Valid @RequestBody EmergencyContactDto dto, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        LocalDateTime now = LocalDateTime.now();

        EmergencyContact contact = repository.findByUserId(userId).orElseGet(() -> {
            EmergencyContact fresh = new EmergencyContact();
            fresh.setUserId(userId);
            fresh.setCreatedAt(now);
            return fresh;
        });
        contact.setName(dto.getName().trim());
        contact.setPhone(dto.getPhone().trim());
        contact.setUpdatedAt(now);
        repository.save(contact);

        return new EmergencyContactDto(contact.getName(), contact.getPhone());
    }

    @DeleteMapping
    @Transactional
    public void delete(Authentication auth) {
        repository.findByUserId((UUID) auth.getPrincipal()).ifPresent(repository::delete);
    }
}
