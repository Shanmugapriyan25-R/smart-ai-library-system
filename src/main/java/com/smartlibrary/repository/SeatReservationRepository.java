package com.smartlibrary.repository;

import com.smartlibrary.entity.ReservationStatus;
import com.smartlibrary.entity.SeatReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    List<SeatReservation> findByUserIdOrderByReservationDateDesc(Long userId);

    Page<SeatReservation> findByUserIdOrderByReservationDateDesc(Long userId, Pageable pageable);

    @Query("SELECT r FROM SeatReservation r WHERE r.reservationDate = :date AND r.status = 'CONFIRMED'")
    List<SeatReservation> findActiveReservationsByDate(@Param("date") LocalDate date);

    @Query("SELECT r FROM SeatReservation r WHERE r.seat.id = :seatId AND r.reservationDate = :date AND r.timeSlot = :slot AND r.status = 'CONFIRMED'")
    Optional<SeatReservation> findActiveBySeatAndDateAndSlot(@Param("seatId") Long seatId,
                                                            @Param("date") LocalDate date,
                                                            @Param("slot") String slot);

    @Query("SELECT r FROM SeatReservation r WHERE r.user.id = :userId AND r.reservationDate = :date AND r.timeSlot = :slot AND r.status = 'CONFIRMED'")
    Optional<SeatReservation> findActiveByUserAndDateAndSlot(@Param("userId") Long userId,
                                                            @Param("date") LocalDate date,
                                                            @Param("slot") String slot);

    @Query("SELECT r FROM SeatReservation r WHERE (:date IS NULL OR r.reservationDate = :date) ORDER BY r.reservationDate DESC, r.timeSlot ASC")
    Page<SeatReservation> findAllReservationsFiltered(@Param("date") LocalDate date, Pageable pageable);

    long countByReservationDateAndStatus(LocalDate date, ReservationStatus status);

    long countByStatus(ReservationStatus status);
}
