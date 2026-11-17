package com.smartlibrary.service;

import com.smartlibrary.dto.SeatReservationDto;
import com.smartlibrary.entity.*;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.repository.LibrarySeatRepository;
import com.smartlibrary.repository.SeatReservationRepository;
import com.smartlibrary.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class SeatReservationService {

    public static final List<String> STANDARD_TIME_SLOTS = List.of(
            "09:00 - 12:00",
            "12:00 - 15:00",
            "15:00 - 18:00",
            "18:00 - 21:00"
    );

    private final LibrarySeatRepository seatRepository;
    private final SeatReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;

    public SeatReservationService(LibrarySeatRepository seatRepository,
                                  SeatReservationRepository reservationRepository,
                                  UserRepository userRepository,
                                  SystemSettingService systemSettingService,
                                  AuditLogService auditLogService) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<LibrarySeat> getAllActiveSeats() {
        return seatRepository.findByStatus("ACTIVE");
    }

    @Transactional(readOnly = true)
    public Map<Long, Set<String>> getBookedSlotsForDate(LocalDate date) {
        List<SeatReservation> reservations = reservationRepository.findActiveReservationsByDate(date);
        Map<Long, Set<String>> map = new HashMap<>();
        for (SeatReservation res : reservations) {
            map.computeIfAbsent(res.getSeat().getId(), k -> new HashSet<>()).add(res.getTimeSlot());
        }
        return map;
    }

    @Transactional
    public SeatReservation createReservation(Long userId, SeatReservationDto dto) {
        if (!systemSettingService.getBooleanSetting("allow_student_reservations", true)) {
            throw new BusinessRuleException("Seat reservations are currently disabled by the library administration.");
        }

        LocalDate targetDate = dto.getReservationDate();
        LocalDate today = LocalDate.now();

        if (targetDate.isBefore(today)) {
            throw new BusinessRuleException("Cannot reserve a seat for a past date.");
        }
        if (targetDate.isAfter(today.plusDays(7))) {
            throw new BusinessRuleException("Reservations can only be made up to 7 days in advance.");
        }

        if (!STANDARD_TIME_SLOTS.contains(dto.getTimeSlot())) {
            throw new BusinessRuleException("Invalid time slot selected. Available slots: " + STANDARD_TIME_SLOTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessRuleException("Your account is currently suspended.");
        }

        LibrarySeat seat = seatRepository.findById(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Library seat not found with ID: " + dto.getSeatId()));

        if (!"ACTIVE".equalsIgnoreCase(seat.getStatus())) {
            throw new BusinessRuleException("Seat " + seat.getSeatNumber() + " is currently unavailable for maintenance.");
        }

        // Prevent overlapping reservations: Does user already have a seat booked for this same date & slot?
        if (reservationRepository.findActiveByUserAndDateAndSlot(userId, targetDate, dto.getTimeSlot()).isPresent()) {
            throw new BusinessRuleException("You already have an active seat reservation on " + targetDate + " for slot " + dto.getTimeSlot() + ".");
        }

        // Concurrency check: Is this specific seat already reserved by someone else for this date & slot?
        if (reservationRepository.findActiveBySeatAndDateAndSlot(dto.getSeatId(), targetDate, dto.getTimeSlot()).isPresent()) {
            throw new BusinessRuleException("Seat " + seat.getSeatNumber() + " is already reserved for " + dto.getTimeSlot() + " on " + targetDate + ". Please choose another seat or slot.");
        }

        SeatReservation reservation = new SeatReservation(user, seat, targetDate, dto.getTimeSlot());
        SeatReservation saved = reservationRepository.save(reservation);

        auditLogService.log(user.getUsername(), "RESERVE_SEAT", "SeatReservation", saved.getId().toString(),
                "Reserved seat " + seat.getSeatNumber() + " on " + targetDate + " (" + dto.getTimeSlot() + ")");

        return saved;
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId, boolean isStaffOrAdmin) {
        SeatReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        if (!isStaffOrAdmin && !reservation.getUser().getId().equals(userId)) {
            throw new BusinessRuleException("You are not authorized to cancel this reservation.");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessRuleException("Reservation cannot be cancelled because its current status is " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        auditLogService.log(reservation.getUser().getUsername(), "CANCEL_SEAT_RESERVATION", "SeatReservation",
                reservationId.toString(), "Cancelled reservation for seat " + reservation.getSeat().getSeatNumber());
    }

    @Transactional(readOnly = true)
    public List<SeatReservation> getUserReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByReservationDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<SeatReservation> getUserReservations(Long userId, Pageable pageable) {
        return reservationRepository.findByUserIdOrderByReservationDateDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SeatReservation> getAllReservations(LocalDate date, Pageable pageable) {
        return reservationRepository.findAllReservationsFiltered(date, pageable);
    }
}
