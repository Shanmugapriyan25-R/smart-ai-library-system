package com.smartlibrary.service;

import com.smartlibrary.entity.*;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.BookTransactionRepository;
import com.smartlibrary.repository.IssueRequestRepository;
import com.smartlibrary.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CirculationService {

    private final BookRepository bookRepository;
    private final IssueRequestRepository issueRequestRepository;
    private final BookTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;

    public CirculationService(BookRepository bookRepository,
                              IssueRequestRepository issueRequestRepository,
                              BookTransactionRepository transactionRepository,
                              UserRepository userRepository,
                              SystemSettingService systemSettingService,
                              AuditLogService auditLogService) {
        this.bookRepository = bookRepository;
        this.issueRequestRepository = issueRequestRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
    }

    /**
     * Student submits an issue request.
     */
    @Transactional
    public IssueRequest requestBookIssue(Long userId, Long bookId, String remarks) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessRuleException("Your account is suspended. You cannot request books.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + bookId));

        if (Boolean.TRUE.equals(book.getIsArchived())) {
            throw new BusinessRuleException("This book is archived and unavailable for loan.");
        }

        if (book.getAvailableCopies() <= 0) {
            throw new BusinessRuleException("No physical copies of '" + book.getTitle() + "' are currently available in the library.");
        }

        // Check if student already has a pending request for this book
        if (issueRequestRepository.findPendingRequestByUserAndBook(userId, bookId).isPresent()) {
            throw new BusinessRuleException("You already have an active pending request for '" + book.getTitle() + "'.");
        }

        // Check student's active borrowing limit
        int maxBorrowLimit = systemSettingService.getIntSetting("max_borrow_limit", 3);
        long activeIssues = transactionRepository.countActiveIssuesByUser(userId);
        long pendingRequests = issueRequestRepository.countByUserIdAndStatus(userId, RequestStatus.PENDING);

        if (activeIssues + pendingRequests >= maxBorrowLimit) {
            throw new BusinessRuleException("Borrowing limit reached: You can have at most " + maxBorrowLimit +
                    " total active loans/pending requests (Currently active: " + activeIssues + ", Pending: " + pendingRequests + ").");
        }

        IssueRequest request = new IssueRequest(user, book, remarks);
        IssueRequest saved = issueRequestRepository.save(request);

        auditLogService.log(user.getUsername(), "ISSUE_REQUEST_SUBMITTED", "IssueRequest", saved.getId().toString(),
                "Requested book: " + book.getTitle());
        return saved;
    }

    /**
     * Student cancels their own pending issue request.
     */
    @Transactional
    public void cancelIssueRequest(Long requestId, Long userId) {
        IssueRequest request = issueRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue request not found with ID: " + requestId));

        if (!request.getUser().getId().equals(userId)) {
            throw new BusinessRuleException("You are not authorized to cancel this issue request.");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleException("Cannot cancel an issue request that is already " + request.getStatus());
        }

        request.setStatus(RequestStatus.CANCELLED);
        issueRequestRepository.save(request);

        auditLogService.log(request.getUser().getUsername(), "ISSUE_REQUEST_CANCELLED", "IssueRequest", requestId.toString(),
                "Cancelled issue request for book: " + request.getBook().getTitle());
    }

    /**
     * Staff/Admin approves an issue request.
     * Uses pessimistic write lock on the book to ensure atomic inventory decrement without race conditions.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BookTransaction approveIssueRequest(Long requestId, Long staffUserId, String remarks) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with ID: " + staffUserId));

        IssueRequest request = issueRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue request not found with ID: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleException("Cannot approve request. Current status is " + request.getStatus());
        }

        // Lock the book row for atomic update
        Book book = bookRepository.findByIdWithPessimisticLock(request.getBook().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + request.getBook().getId()));

        if (book.getAvailableCopies() <= 0) {
            throw new BusinessRuleException("Cannot approve issue request: No available copies remaining for '" + book.getTitle() + "'.");
        }

        // Check borrower active limit again at approval time
        int maxBorrowLimit = systemSettingService.getIntSetting("max_borrow_limit", 3);
        long activeIssues = transactionRepository.countActiveIssuesByUser(request.getUser().getId());
        if (activeIssues >= maxBorrowLimit) {
            throw new BusinessRuleException("Cannot approve: User has already reached maximum loan limit of " + maxBorrowLimit);
        }

        // Atomically decrement available copies
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        // Update request status
        request.setStatus(RequestStatus.APPROVED);
        request.setProcessedBy(staff);
        request.setProcessedDate(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            request.setRemarks(remarks);
        }
        issueRequestRepository.save(request);

        // Create transaction with due date
        int borrowDays = systemSettingService.getIntSetting("borrow_duration_days", 14);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueDate = now.plusDays(borrowDays);

        BookTransaction transaction = new BookTransaction();
        transaction.setUser(request.getUser());
        transaction.setBook(book);
        transaction.setIssueRequest(request);
        transaction.setIssueDate(now);
        transaction.setDueDate(dueDate);
        transaction.setStatus(TransactionStatus.ISSUED);
        transaction.setIssuedBy(staff);
        transaction.setRemarks(remarks);

        BookTransaction savedTransaction = transactionRepository.save(transaction);

        auditLogService.log(staff.getUsername(), "ISSUE_REQUEST_APPROVED", "BookTransaction",
                savedTransaction.getId().toString(),
                "Issued '" + book.getTitle() + "' to student " + request.getUser().getUsername() + " (Due: " + dueDate.toLocalDate() + ")");

        return savedTransaction;
    }

    /**
     * Staff/Admin rejects an issue request.
     */
    @Transactional
    public void rejectIssueRequest(Long requestId, Long staffUserId, String reason) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with ID: " + staffUserId));

        IssueRequest request = issueRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue request not found with ID: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleException("Cannot reject request. Current status is " + request.getStatus());
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setProcessedBy(staff);
        request.setProcessedDate(LocalDateTime.now());
        request.setRemarks(reason != null ? reason : "Request rejected by library staff");
        issueRequestRepository.save(request);

        auditLogService.log(staff.getUsername(), "ISSUE_REQUEST_REJECTED", "IssueRequest", requestId.toString(),
                "Rejected issue request for " + request.getBook().getTitle() + " by " + request.getUser().getUsername());
    }

    /**
     * Direct book issue by Staff/Admin (e.g. over-the-counter checkout without web request).
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BookTransaction issueBookDirect(Long userId, Long bookId, Long staffUserId, String remarks) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + userId));
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found with ID: " + staffUserId));

        int maxBorrowLimit = systemSettingService.getIntSetting("max_borrow_limit", 3);
        long activeIssues = transactionRepository.countActiveIssuesByUser(userId);
        if (activeIssues >= maxBorrowLimit) {
            throw new BusinessRuleException("Student has reached maximum active loan limit of " + maxBorrowLimit);
        }

        Book book = bookRepository.findByIdWithPessimisticLock(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + bookId));

        if (book.getAvailableCopies() <= 0) {
            throw new BusinessRuleException("No copies available for '" + book.getTitle() + "'.");
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        int borrowDays = systemSettingService.getIntSetting("borrow_duration_days", 14);
        LocalDateTime now = LocalDateTime.now();

        BookTransaction transaction = new BookTransaction();
        transaction.setUser(user);
        transaction.setBook(book);
        transaction.setIssueDate(now);
        transaction.setDueDate(now.plusDays(borrowDays));
        transaction.setStatus(TransactionStatus.ISSUED);
        transaction.setIssuedBy(staff);
        transaction.setRemarks(remarks);

        BookTransaction saved = transactionRepository.save(transaction);
        auditLogService.log(staff.getUsername(), "DIRECT_BOOK_ISSUE", "BookTransaction", saved.getId().toString(),
                "Directly issued '" + book.getTitle() + "' to " + user.getUsername());
        return saved;
    }

    /**
     * Staff/Admin records the return of a borrowed book.
     * Restores inventory atomically and computes any overdue fines.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BookTransaction recordBookReturn(Long transactionId, Long staffUserId, String remarks) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with ID: " + staffUserId));

        BookTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

        if (transaction.getStatus() == TransactionStatus.RETURNED) {
            throw new BusinessRuleException("This book has already been returned on " + transaction.getReturnDate());
        }

        LocalDateTime returnTime = LocalDateTime.now();
        transaction.setReturnDate(returnTime);
        transaction.setStatus(TransactionStatus.RETURNED);
        transaction.setReceivedBy(staff);

        if (remarks != null && !remarks.isBlank()) {
            transaction.setRemarks(remarks);
        }

        // Calculate overdue fine if applicable
        if (returnTime.isAfter(transaction.getDueDate())) {
            long daysOverdue = Math.max(1, Duration.between(transaction.getDueDate(), returnTime).toDays());
            double fineRate = systemSettingService.getDoubleSetting("daily_fine_rate", 1.00);
            BigDecimal totalFine = BigDecimal.valueOf(daysOverdue * fineRate);
            transaction.setFineAmount(totalFine);
        }

        // Atomically restore available inventory with lock
        Book book = bookRepository.findByIdWithPessimisticLock(transaction.getBook().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + transaction.getBook().getId()));

        if (book.getAvailableCopies() < book.getTotalCopies()) {
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            bookRepository.save(book);
        }

        BookTransaction saved = transactionRepository.save(transaction);
        auditLogService.log(staff.getUsername(), "BOOK_RETURN_RECORDED", "BookTransaction", saved.getId().toString(),
                "Recorded return of '" + book.getTitle() + "' from " + transaction.getUser().getUsername() +
                        (transaction.getFineAmount().compareTo(BigDecimal.ZERO) > 0 ? " (Fine: $" + transaction.getFineAmount() + ")" : ""));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<IssueRequest> getPendingRequests() {
        return issueRequestRepository.findByStatusOrderByRequestDateAsc(RequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Page<IssueRequest> getPendingRequests(Pageable pageable) {
        return issueRequestRepository.findByStatusOrderByRequestDateAsc(RequestStatus.PENDING, pageable);
    }

    @Transactional(readOnly = true)
    public List<IssueRequest> getUserRequests(Long userId) {
        return issueRequestRepository.findByUserIdOrderByRequestDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<BookTransaction> getUserTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByIssueDateDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<BookTransaction> getUserActiveLoans(Long userId) {
        return transactionRepository.findByUserIdAndStatus(userId, TransactionStatus.ISSUED);
    }

    @Transactional(readOnly = true)
    public Page<BookTransaction> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<BookTransaction> getOverdueTransactions(Pageable pageable) {
        return transactionRepository.findOverdueTransactions(LocalDateTime.now(), pageable);
    }
}
