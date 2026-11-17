package com.smartlibrary.service;

import com.smartlibrary.dto.DashboardSummaryDto;
import com.smartlibrary.entity.ReservationStatus;
import com.smartlibrary.entity.Role;
import com.smartlibrary.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AnalyticsService {

    private final BookRepository bookRepository;
    private final BookTransactionRepository transactionRepository;
    private final IssueRequestRepository issueRequestRepository;
    private final UserRepository userRepository;
    private final SeatReservationRepository reservationRepository;
    private final StudySessionRepository studySessionRepository;

    public AnalyticsService(BookRepository bookRepository,
                            BookTransactionRepository transactionRepository,
                            IssueRequestRepository issueRequestRepository,
                            UserRepository userRepository,
                            SeatReservationRepository reservationRepository,
                            StudySessionRepository studySessionRepository) {
        this.bookRepository = bookRepository;
        this.transactionRepository = transactionRepository;
        this.issueRequestRepository = issueRequestRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.studySessionRepository = studySessionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSystemSummary() {
        DashboardSummaryDto dto = new DashboardSummaryDto();
        dto.setTotalBooks(bookRepository.countActiveBooks());
        dto.setTotalCopies(bookRepository.sumTotalCopies());
        dto.setAvailableCopies(bookRepository.sumAvailableCopies());
        dto.setActiveBorrowings(transactionRepository.countCurrentlyIssued());
        dto.setPendingRequests(issueRequestRepository.countByStatus(com.smartlibrary.entity.RequestStatus.PENDING));
        dto.setOverdueCount(transactionRepository.countOverdueTransactions(LocalDateTime.now()));
        dto.setTotalStudents(userRepository.countByRole(Role.ROLE_STUDENT));
        dto.setTotalStaff(userRepository.countByRole(Role.ROLE_STAFF));
        dto.setActiveReservations(reservationRepository.countByReservationDateAndStatus(LocalDate.now(), ReservationStatus.CONFIRMED));
        return dto;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getStudentSummary(Long studentId) {
        DashboardSummaryDto dto = new DashboardSummaryDto();
        dto.setActiveBorrowings(transactionRepository.countActiveIssuesByUser(studentId));
        dto.setPendingRequests(issueRequestRepository.countByUserIdAndStatus(studentId, com.smartlibrary.entity.RequestStatus.PENDING));
        dto.setStudyMinutesLogged(studySessionRepository.sumStudyMinutesByUser(studentId));
        return dto;
    }

    @Transactional(readOnly = true)
    public long getTodayIssuesCount() {
        return transactionRepository.countIssuesSince(LocalDate.now().atStartOfDay());
    }

    @Transactional(readOnly = true)
    public long getTodayReturnsCount() {
        return transactionRepository.countReturnsSince(LocalDate.now().atStartOfDay());
    }
}
