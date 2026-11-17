package com.smartlibrary.controller;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookTransaction;
import com.smartlibrary.entity.IssueRequest;
import com.smartlibrary.entity.Role;
import com.smartlibrary.security.CustomUserDetails;
import com.smartlibrary.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/staff")
public class StaffController {

    private final BookService bookService;
    private final CirculationService circulationService;
    private final SeatReservationService reservationService;
    private final UserService userService;
    private final AnalyticsService analyticsService;

    public StaffController(BookService bookService,
                           CirculationService circulationService,
                           SeatReservationService reservationService,
                           UserService userService,
                           AnalyticsService analyticsService) {
        this.bookService = bookService;
        this.circulationService = circulationService;
        this.reservationService = reservationService;
        this.userService = userService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", analyticsService.getSystemSummary());
        model.addAttribute("todayIssues", analyticsService.getTodayIssuesCount());
        model.addAttribute("todayReturns", analyticsService.getTodayReturnsCount());
        model.addAttribute("pendingRequests", circulationService.getPendingRequests());
        model.addAttribute("overdueList", circulationService.getOverdueTransactions(PageRequest.of(0, 5)).getContent());
        return "staff/dashboard";
    }

    @GetMapping("/requests")
    public String viewRequests(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        Page<IssueRequest> requests = circulationService.getPendingRequests(PageRequest.of(page, 15));
        model.addAttribute("requests", requests);
        return "staff/requests";
    }

    @PostMapping("/approve-request/{id}")
    public String approveRequest(@PathVariable("id") Long requestId,
                                 @RequestParam(value = "remarks", required = false) String remarks,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            circulationService.approveIssueRequest(requestId, userDetails.getId(), remarks);
            redirectAttributes.addFlashAttribute("successMessage", "Book issue request approved successfully! Book marked as loaned.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/requests";
    }

    @PostMapping("/reject-request/{id}")
    public String rejectRequest(@PathVariable("id") Long requestId,
                                @RequestParam(value = "reason", required = false) String reason,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        try {
            circulationService.rejectIssueRequest(requestId, userDetails.getId(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Issue request rejected.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/requests";
    }

    @GetMapping("/direct-issue")
    public String directIssueForm(Model model) {
        model.addAttribute("students", userService.findUsersByRole(Role.ROLE_STUDENT, PageRequest.of(0, 100)).getContent());
        model.addAttribute("books", bookService.getAllActiveBooks().stream().filter(Book::isAvailable).toList());
        return "staff/direct-issue";
    }

    @PostMapping("/direct-issue")
    public String processDirectIssue(@RequestParam("studentId") Long studentId,
                                     @RequestParam("bookId") Long bookId,
                                     @RequestParam(value = "remarks", required = false) String remarks,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        try {
            circulationService.issueBookDirect(studentId, bookId, userDetails.getId(), remarks);
            redirectAttributes.addFlashAttribute("successMessage", "Book directly issued to student successfully.");
            return "redirect:/staff/returns";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/staff/direct-issue";
        }
    }

    @GetMapping("/returns")
    public String returnsPortal(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        Page<BookTransaction> transactions = circulationService.getAllTransactions(PageRequest.of(page, 15, Sort.by("issueDate").descending()));
        model.addAttribute("transactions", transactions);
        return "staff/returns";
    }

    @PostMapping("/record-return/{id}")
    public String recordReturn(@PathVariable("id") Long transactionId,
                               @RequestParam(value = "remarks", required = false) String remarks,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            BookTransaction transaction = circulationService.recordBookReturn(transactionId, userDetails.getId(), remarks);
            if (transaction.getFineAmount().doubleValue() > 0) {
                redirectAttributes.addFlashAttribute("warningMessage", "Book return recorded. Overdue fine calculated: $" + transaction.getFineAmount());
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Book return recorded successfully. Inventory copy restored.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/returns";
    }

    @GetMapping("/overdue")
    public String overdueTransactions(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        Page<BookTransaction> overdue = circulationService.getOverdueTransactions(PageRequest.of(page, 15));
        model.addAttribute("overdueList", overdue);
        return "staff/overdue";
    }

    @GetMapping("/catalogue")
    public String catalogue(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        Page<Book> books = bookService.searchBooks(query, null, false, PageRequest.of(page, 12, Sort.by("title").ascending()));
        model.addAttribute("books", books);
        model.addAttribute("query", query);
        return "staff/catalogue";
    }

    @GetMapping("/students")
    public String studentRecords(@RequestParam(value = "query", required = false) String query,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 Model model) {
        if (query != null && !query.isBlank()) {
            model.addAttribute("students", userService.searchUsers(query).stream()
                    .filter(u -> u.getRole() == Role.ROLE_STUDENT).toList());
        } else {
            model.addAttribute("students", userService.findUsersByRole(Role.ROLE_STUDENT, PageRequest.of(page, 20)).getContent());
        }
        model.addAttribute("query", query);
        return "staff/students";
    }

    @GetMapping("/seats")
    public String monitorSeats(@RequestParam(value = "date", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(value = "page", defaultValue = "0") int page,
                               Model model) {
        LocalDate selectedDate = (date != null) ? date : LocalDate.now();
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("reservations", reservationService.getAllReservations(selectedDate, PageRequest.of(page, 20)));
        model.addAttribute("seats", reservationService.getAllActiveSeats());
        return "staff/seats";
    }
}
