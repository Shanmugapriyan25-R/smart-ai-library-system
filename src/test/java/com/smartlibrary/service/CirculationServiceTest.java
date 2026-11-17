package com.smartlibrary.service;

import com.smartlibrary.entity.*;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.BookTransactionRepository;
import com.smartlibrary.repository.IssueRequestRepository;
import com.smartlibrary.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CirculationServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private IssueRequestRepository issueRequestRepository;

    @Mock
    private BookTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CirculationService circulationService;

    private User student;
    private User staff;
    private Book book;
    private Category category;

    @BeforeEach
    void setUp() {
        student = new User("student1", "pwd", "Alex Morgan", "student@test.com", Role.ROLE_STUDENT, "123");
        student.setId(10L);
        student.setStatus("ACTIVE");

        staff = new User("staff1", "pwd", "Eleanor Vance", "staff@test.com", Role.ROLE_STAFF, "456");
        staff.setId(20L);
        staff.setStatus("ACTIVE");

        category = new Category("Computer Science", "CS", "CS books");
        category.setId(1L);

        book = new Book();
        book.setId(100L);
        book.setIsbn("978-0134685991");
        book.setTitle("Effective Java");
        book.setAuthor("Joshua Bloch");
        book.setCategory(category);
        book.setTotalCopies(5);
        book.setAvailableCopies(2);
        book.setIsArchived(false);
    }

    @Test
    @DisplayName("Student submits issue request successfully when copies are available")
    void testRequestBookIssueSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
        when(issueRequestRepository.findPendingRequestByUserAndBook(10L, 100L)).thenReturn(Optional.empty());
        when(systemSettingService.getIntSetting("max_borrow_limit", 3)).thenReturn(3);
        when(transactionRepository.countActiveIssuesByUser(10L)).thenReturn(1L);
        when(issueRequestRepository.countByUserIdAndStatus(10L, RequestStatus.PENDING)).thenReturn(0L);
        when(issueRequestRepository.save(any(IssueRequest.class))).thenAnswer(i -> {
            IssueRequest r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        IssueRequest request = circulationService.requestBookIssue(10L, 100L, "Urgent for exams");

        assertThat(request).isNotNull();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getBook().getTitle()).isEqualTo("Effective Java");
        verify(issueRequestRepository).save(any(IssueRequest.class));
    }

    @Test
    @DisplayName("Issue request fails when available copies is zero")
    void testRequestBookIssueOutOfStock() {
        book.setAvailableCopies(0);
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(bookRepository.findById(100L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> circulationService.requestBookIssue(10L, 100L, "Need book"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No physical copies");
    }

    @Test
    @DisplayName("Issue request fails when student exceeds max borrow limit")
    void testRequestBookIssueLimitExceeded() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
        when(issueRequestRepository.findPendingRequestByUserAndBook(10L, 100L)).thenReturn(Optional.empty());
        when(systemSettingService.getIntSetting("max_borrow_limit", 3)).thenReturn(3);
        when(transactionRepository.countActiveIssuesByUser(10L)).thenReturn(3L);
        when(issueRequestRepository.countByUserIdAndStatus(10L, RequestStatus.PENDING)).thenReturn(0L);

        assertThatThrownBy(() -> circulationService.requestBookIssue(10L, 100L, "Another book"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Borrowing limit reached");
    }

    @Test
    @DisplayName("Staff approves issue request: decrements inventory atomically and generates transaction")
    void testApproveIssueRequestSuccess() {
        IssueRequest request = new IssueRequest(student, book, "Study");
        request.setId(5L);
        request.setStatus(RequestStatus.PENDING);

        when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
        when(issueRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(bookRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(book));
        when(systemSettingService.getIntSetting("max_borrow_limit", 3)).thenReturn(3);
        when(transactionRepository.countActiveIssuesByUser(10L)).thenReturn(0L);
        when(systemSettingService.getIntSetting("borrow_duration_days", 14)).thenReturn(14);
        when(transactionRepository.save(any(BookTransaction.class))).thenAnswer(i -> {
            BookTransaction tx = i.getArgument(0);
            tx.setId(50L);
            return tx;
        });

        BookTransaction tx = circulationService.approveIssueRequest(5L, 20L, "Approved at desk");

        assertThat(tx).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.ISSUED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(book.getAvailableCopies()).isEqualTo(1); // decremented from 2 to 1
        verify(bookRepository).save(book);
        verify(transactionRepository).save(any(BookTransaction.class));
    }

    @Test
    @DisplayName("Staff records book return: restores available inventory and calculates fine if overdue")
    void testRecordBookReturnWithFine() {
        BookTransaction transaction = new BookTransaction();
        transaction.setId(50L);
        transaction.setUser(student);
        transaction.setBook(book);
        transaction.setIssueDate(LocalDateTime.now().minusDays(20));
        transaction.setDueDate(LocalDateTime.now().minusDays(6)); // 6 days overdue
        transaction.setStatus(TransactionStatus.ISSUED);
        transaction.setIssuedBy(staff);

        when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(transaction));
        when(systemSettingService.getDoubleSetting("daily_fine_rate", 1.00)).thenReturn(1.50);
        when(bookRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(book));
        when(transactionRepository.save(any(BookTransaction.class))).thenAnswer(i -> i.getArgument(0));

        BookTransaction returned = circulationService.recordBookReturn(50L, 20L, "Returned in person");

        assertThat(returned.getStatus()).isEqualTo(TransactionStatus.RETURNED);
        assertThat(returned.getReturnDate()).isNotNull();
        assertThat(book.getAvailableCopies()).isEqualTo(3); // restored from 2 to 3
        assertThat(returned.getFineAmount()).isGreaterThan(BigDecimal.ZERO);
        verify(bookRepository).save(book);
    }

    @Test
    @DisplayName("Duplicate return is rejected with business rule exception")
    void testDuplicateReturnRejected() {
        BookTransaction transaction = new BookTransaction();
        transaction.setId(50L);
        transaction.setStatus(TransactionStatus.RETURNED);
        transaction.setReturnDate(LocalDateTime.now().minusDays(1));

        when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> circulationService.recordBookReturn(50L, 20L, "Return again"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been returned");
    }
}
