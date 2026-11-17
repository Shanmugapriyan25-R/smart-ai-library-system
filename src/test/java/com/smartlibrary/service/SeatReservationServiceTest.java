package com.smartlibrary.service;

import com.smartlibrary.dto.SeatReservationDto;
import com.smartlibrary.entity.*;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.repository.LibrarySeatRepository;
import com.smartlibrary.repository.SeatReservationRepository;
import com.smartlibrary.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatReservationServiceTest {

    @Mock
    private LibrarySeatRepository seatRepository;

    @Mock
    private SeatReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SeatReservationService reservationService;

    private User student;
    private LibrarySeat seat;

    @BeforeEach
    void setUp() {
        student = new User("student1", "pwd", "Alex Morgan", "student@test.com", Role.ROLE_STUDENT, "123");
        student.setId(10L);
        student.setStatus("ACTIVE");

        seat = new LibrarySeat("S-101", "1st Floor", "Quiet Zone", true, true);
        seat.setId(1L);
        seat.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("Successfully reserve available library seat")
    void testCreateReservationSuccess() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SeatReservationDto dto = new SeatReservationDto();
        dto.setSeatId(1L);
        dto.setReservationDate(tomorrow);
        dto.setTimeSlot("09:00 - 12:00");

        when(systemSettingService.getBooleanSetting("allow_student_reservations", true)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(reservationRepository.findActiveByUserAndDateAndSlot(10L, tomorrow, "09:00 - 12:00")).thenReturn(Optional.empty());
        when(reservationRepository.findActiveBySeatAndDateAndSlot(1L, tomorrow, "09:00 - 12:00")).thenReturn(Optional.empty());
        when(reservationRepository.save(any(SeatReservation.class))).thenAnswer(i -> {
            SeatReservation r = i.getArgument(0);
            r.setId(99L);
            return r;
        });

        SeatReservation res = reservationService.createReservation(10L, dto);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(res.getSeat().getSeatNumber()).isEqualTo("S-101");
        verify(reservationRepository).save(any(SeatReservation.class));
    }

    @Test
    @DisplayName("Fail reservation when student already booked a desk in that same slot")
    void testCreateReservationUserDoubleBooking() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SeatReservationDto dto = new SeatReservationDto();
        dto.setSeatId(1L);
        dto.setReservationDate(tomorrow);
        dto.setTimeSlot("09:00 - 12:00");

        when(systemSettingService.getBooleanSetting("allow_student_reservations", true)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(reservationRepository.findActiveByUserAndDateAndSlot(10L, tomorrow, "09:00 - 12:00"))
                .thenReturn(Optional.of(new SeatReservation()));

        assertThatThrownBy(() -> reservationService.createReservation(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already have an active seat reservation");
    }

    @Test
    @DisplayName("Fail reservation when seat is already reserved by someone else (conflict prevention)")
    void testCreateReservationSeatConflict() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SeatReservationDto dto = new SeatReservationDto();
        dto.setSeatId(1L);
        dto.setReservationDate(tomorrow);
        dto.setTimeSlot("09:00 - 12:00");

        when(systemSettingService.getBooleanSetting("allow_student_reservations", true)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(reservationRepository.findActiveByUserAndDateAndSlot(10L, tomorrow, "09:00 - 12:00")).thenReturn(Optional.empty());
        when(reservationRepository.findActiveBySeatAndDateAndSlot(1L, tomorrow, "09:00 - 12:00"))
                .thenReturn(Optional.of(new SeatReservation()));

        assertThatThrownBy(() -> reservationService.createReservation(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already reserved for");
    }

    @Test
    @DisplayName("Fail reservation when attempting to book past date")
    void testCreateReservationPastDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SeatReservationDto dto = new SeatReservationDto();
        dto.setSeatId(1L);
        dto.setReservationDate(yesterday);
        dto.setTimeSlot("09:00 - 12:00");

        when(systemSettingService.getBooleanSetting("allow_student_reservations", true)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.createReservation(10L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("past date");
    }
}
