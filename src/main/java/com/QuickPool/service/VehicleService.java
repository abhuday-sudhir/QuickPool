package com.QuickPool.service;

import com.QuickPool.dtos.SaveVehicleDto;
import com.QuickPool.dtos.VehicleDto;
import com.QuickPool.entity.Vehicle;
import com.QuickPool.repository.VehicleRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class VehicleService {

    @Autowired
    private VehicleRepository repository;

    public VehicleDto forUser(UUID userId) {
        return repository.findByUserId(userId).map(VehicleDto::new).orElse(null);
    }

    /** One vehicle per driver — saving again updates the existing row. */
    @Transactional
    public VehicleDto save(UUID userId, SaveVehicleDto dto) {
        LocalDateTime now = LocalDateTime.now();
        Vehicle vehicle = repository.findByUserId(userId).orElseGet(() -> {
            Vehicle fresh = new Vehicle();
            fresh.setUserId(userId);
            fresh.setCreatedAt(now);
            return fresh;
        });

        vehicle.setMake(dto.getMake().trim());
        vehicle.setModel(dto.getModel().trim());
        vehicle.setColor(dto.getColor().trim());
        vehicle.setPlate(normalisePlate(dto.getPlate()));
        vehicle.setUpdatedAt(now);

        return new VehicleDto(repository.save(vehicle));
    }

    @Transactional
    public void delete(UUID userId) {
        repository.findByUserId(userId).ifPresent(repository::delete);
    }

    /** "dl 3c ab-1234" and "DL3CAB1234" are the same plate. */
    private static String normalisePlate(String raw) {
        return raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
