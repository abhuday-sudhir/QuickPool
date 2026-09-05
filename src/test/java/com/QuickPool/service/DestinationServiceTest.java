package com.QuickPool.service;

import com.QuickPool.dtos.DestinationDto;
import com.QuickPool.entity.UserDestination;
import com.QuickPool.repository.UserDestinationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock private UserDestinationRepository repository;
    @InjectMocks private DestinationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("record() creates a new row for a place seen for the first time")
    void recordsNewDestination() {
        when(repository.findByUserIdAndGeoKey(eq(userId), any())).thenReturn(Optional.empty());

        service.record(userId, "Home", 12.3456, 77.6543);

        var captor = org.mockito.ArgumentCaptor.forClass(UserDestination.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUseCount()).isEqualTo(1);
        assertThat(captor.getValue().getGeoKey()).isEqualTo("12.3456,77.6543");
    }

    @Test
    @DisplayName("record() bumps the counter and refreshes the label for a place already known")
    void bumpsExistingDestination() {
        UserDestination existing = new UserDestination();
        existing.setUserId(userId);
        existing.setName("Old label");
        existing.setUseCount(4);
        when(repository.findByUserIdAndGeoKey(eq(userId), any())).thenReturn(Optional.of(existing));

        service.record(userId, "New label", 12.3456, 77.6543);

        assertThat(existing.getUseCount()).isEqualTo(5);
        assertThat(existing.getName()).isEqualTo("New label");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("record() keeps the old label when a blank name is passed for a known place")
    void keepsOldLabelWhenNameBlank() {
        UserDestination existing = new UserDestination();
        existing.setUserId(userId);
        existing.setName("Keep me");
        existing.setUseCount(1);
        when(repository.findByUserIdAndGeoKey(eq(userId), any())).thenReturn(Optional.of(existing));

        service.record(userId, "  ", 12.3456, 77.6543);

        assertThat(existing.getName()).isEqualTo("Keep me");
    }

    @Test
    @DisplayName("record() falls back to bumping the row when a concurrent insert wins the race")
    void racingInsertFallsBackToBump() {
        when(repository.findByUserIdAndGeoKey(eq(userId), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace()));
        when(repository.save(argThat(d -> d.getId() == null)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        service.record(userId, "Home", 12.3456, 77.6543);

        // Once for the failed insert attempt, once for the fallback bump.
        verify(repository, times(2)).save(any());
    }

    private UserDestination existingAfterRace() {
        UserDestination d = new UserDestination();
        d.setId(UUID.randomUUID());
        d.setUserId(userId);
        d.setUseCount(1);
        return d;
    }

    @Test
    @DisplayName("getFrequent() maps repository rows into DestinationDto")
    void getFrequentMapsRows() {
        UserDestination d = new UserDestination();
        d.setName("Office");
        d.setLat(1.0);
        d.setLng(2.0);
        d.setUseCount(7);
        when(repository.findByUserIdOrderByUseCountDescLastUsedAtDesc(eq(userId), any()))
                .thenReturn(List.of(d));

        List<DestinationDto> result = service.getFrequent(userId);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getName()).isEqualTo("Office");
            assertThat(dto.getUseCount()).isEqualTo(7);
        });
    }
}
