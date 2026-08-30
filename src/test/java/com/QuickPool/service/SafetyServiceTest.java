package com.QuickPool.service;

import com.QuickPool.dtos.ReportUserDto;
import com.QuickPool.entity.UserBlock;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.UserBlockRepository;
import com.QuickPool.repository.UserReportRepository;
import com.QuickPool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyServiceTest {

    @Mock private UserBlockRepository blockRepository;
    @Mock private UserReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private SafetyService service;

    private final UUID me = UUID.randomUUID();
    private final UUID them = UUID.randomUUID();

    @Test
    @DisplayName("cannot block yourself")
    void noSelfBlock() {
        assertThatThrownBy(() -> service.block(me, me)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("blocking an unknown user is a 404, not a silent write")
    void blockUnknownUser() {
        when(userRepository.existsById(them)).thenReturn(false);
        assertThatThrownBy(() -> service.block(me, them)).isInstanceOf(NotFoundException.class);
        verify(blockRepository, never()).save(any());
    }

    @Test
    @DisplayName("blocking twice is a no-op rather than an error")
    void blockingTwiceIsIdempotent() {
        when(userRepository.existsById(them)).thenReturn(true);
        when(blockRepository.findByBlockerIdAndBlockedId(me, them))
                .thenReturn(Optional.of(new UserBlock()));

        service.block(me, them);

        verify(blockRepository, never()).save(any());
    }

    @Test
    @DisplayName("isHidden is symmetric: it does not matter who blocked whom")
    void hidingIsSymmetric() {
        when(blockRepository.findByBlockerIdAndBlockedId(me, them)).thenReturn(Optional.empty());
        when(blockRepository.findByBlockerIdAndBlockedId(them, me))
                .thenReturn(Optional.of(new UserBlock()));

        assertThat(service.isHidden(me, them)).isTrue();
    }

    @Test
    @DisplayName("hiddenFrom collects both directions")
    void hiddenFromBothDirections() {
        when(blockRepository.findHiddenUserIds(me)).thenReturn(List.of(them));
        assertThat(service.hiddenFrom(me)).containsExactly(them);
    }

    @Test
    @DisplayName("cannot report yourself")
    void noSelfReport() {
        ReportUserDto dto = new ReportUserDto();
        dto.setReportedId(me);
        dto.setReason("OTHER");
        assertThatThrownBy(() -> service.report(me, dto)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("one open report per person at a time")
    void oneOpenReportPerPerson() {
        ReportUserDto dto = new ReportUserDto();
        dto.setReportedId(them);
        dto.setReason("UNSAFE_DRIVING");
        when(userRepository.existsById(them)).thenReturn(true);
        when(reportRepository.existsByReporterIdAndReportedIdAndStatus(me, them, "OPEN")).thenReturn(true);

        assertThatThrownBy(() -> service.report(me, dto)).isInstanceOf(ConflictException.class);
        verify(reportRepository, never()).save(any());
    }
}
