package com.QuickPool.service;

import com.QuickPool.dtos.SaveVehicleDto;
import com.QuickPool.dtos.VehicleDto;
import com.QuickPool.entity.Vehicle;
import com.QuickPool.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository repository;
    @InjectMocks private VehicleService service;

    private final UUID user = UUID.randomUUID();

    private SaveVehicleDto dto(String plate) {
        SaveVehicleDto d = new SaveVehicleDto();
        d.setMake(" Maruti ");
        d.setModel(" Swift ");
        d.setColor(" White ");
        d.setPlate(plate);
        return d;
    }

    @Test
    @DisplayName("normalises the plate to uppercase alphanumerics")
    void normalisesPlate() {
        when(repository.findByUserId(user)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        VehicleDto saved = service.save(user, dto("dl 3c ab-1234"));

        assertThat(saved.getPlate()).isEqualTo("DL3CAB1234");
        assertThat(saved.getMake()).isEqualTo("Maruti");   // trimmed
    }

    @Test
    @DisplayName("saving again updates the existing vehicle rather than adding one")
    void updatesInPlace() {
        Vehicle existing = new Vehicle();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        existing.setPlate("OLD123");
        when(repository.findByUserId(user)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.save(user, dto("UP80AB1111"));

        verify(repository).save(existing);   // same row, not a new one
        assertThat(existing.getPlate()).isEqualTo("UP80AB1111");
    }

    @Test
    @DisplayName("describe() reads the way a passenger would look for the car")
    void describesForPassenger() {
        Vehicle v = new Vehicle();
        v.setMake("Honda");
        v.setModel("City");
        v.setColor("Grey");
        v.setPlate("UP80AB1111");

        assertThat(new VehicleDto(v).describe()).isEqualTo("Grey Honda City · UP80AB1111");
    }
}
