package com.QuickPool.service;

import com.QuickPool.dtos.SaveAddressDto;
import com.QuickPool.dtos.SavedAddressDto;
import com.QuickPool.entity.SavedAddress;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.SavedAddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedAddressServiceTest {

    @Mock private SavedAddressRepository repository;
    @InjectMocks private SavedAddressService service;

    private final UUID userId = UUID.randomUUID();

    private SaveAddressDto dto(String label) {
        SaveAddressDto d = new SaveAddressDto();
        d.setLabel(label);
        d.setName("221B Baker St");
        d.setLat(1.0);
        d.setLng(2.0);
        return d;
    }

    @Test
    @DisplayName("saves a brand new address under the per-user cap")
    void savesNewAddress() {
        when(repository.findByUserIdAndLabelIgnoreCase(userId, "Home")).thenReturn(Optional.empty());
        when(repository.countByUserId(userId)).thenReturn(3L);
        when(repository.save(any())).thenAnswer(i -> {
            SavedAddress a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        SavedAddressDto result = service.save(userId, dto("Home"));

        assertThat(result.getLabel()).isEqualTo("Home");
        verify(repository).save(any());
    }

    @Test
    @DisplayName("refuses a new label once the per-user cap is reached")
    void refusesOverCap() {
        when(repository.findByUserIdAndLabelIgnoreCase(userId, "Work")).thenReturn(Optional.empty());
        when(repository.countByUserId(userId)).thenReturn(12L);

        assertThatThrownBy(() -> service.save(userId, dto("Work"))).isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("saving the same label again overwrites the existing row instead of adding one")
    void overwritesExistingLabel() {
        SavedAddress existing = new SavedAddress();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setLabel("Home");
        existing.setName("Old address");
        when(repository.findByUserIdAndLabelIgnoreCase(userId, "Home")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.save(userId, dto("Home"));

        verify(repository, never()).countByUserId(any());
        verify(repository).save(existing);
        assertThat(existing.getName()).isEqualTo("221B Baker St");
    }

    @Test
    @DisplayName("delete() 404s for an address id that doesn't exist")
    void deleteMissing() {
        UUID addressId = UUID.randomUUID();
        when(repository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, addressId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("delete() refuses to remove someone else's address")
    void deleteRefusesOtherOwner() {
        UUID addressId = UUID.randomUUID();
        SavedAddress address = new SavedAddress();
        address.setUserId(UUID.randomUUID());
        when(repository.findById(addressId)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> service.delete(userId, addressId)).isInstanceOf(ForbiddenException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("delete() removes the caller's own address")
    void deleteHappyPath() {
        UUID addressId = UUID.randomUUID();
        SavedAddress address = new SavedAddress();
        address.setUserId(userId);
        when(repository.findById(addressId)).thenReturn(Optional.of(address));

        service.delete(userId, addressId);

        verify(repository).delete(address);
    }
}
