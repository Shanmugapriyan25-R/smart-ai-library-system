package com.smartlibrary.controller;

import com.smartlibrary.dto.IssueRequestDto;
import com.smartlibrary.dto.SeatReservationDto;
import com.smartlibrary.entity.*;
import com.smartlibrary.security.CustomUserDetails;
import com.smartlibrary.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final BookService bookService;
    private final CirculationService circulationService;
    private final SeatReservationService reservationService;
    private final DigitalResourceService digitalResourceService;
    private final StudySessionService studySessionService;
    private final UserService userService;
    private final AnalyticsService analyticsService;

    public StudentController(BookService bookService,
                             CirculationService circulationService,
                             SeatReservationService reservationService,
                             DigitalResourceService digitalResourceService,
                             StudySessionService studySessionService,
                             UserService userService,
                             AnalyticsService analyticsService) {
        this.bookService = bookService;
        this.circulationService = circulationService;
        this.reservationService = reservationService;
        this.digitalResourceService = digitalResourceService;
        this.studySessionService = studySessionService;
        this.userService = userService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long userId = userDetails.getId();
        model.addAttribute("user", userDetails.getUser());
        model.addAttribute("summary", analyticsService.getStudentSummary(userId));
        model.addAttribute("activeLoans", circulationService.getUserActiveLoans(userId));
        model.addAttribute("pendingRequests", circulationService.getUserRequests(userId).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING).toList());
        model.addAttribute("upcomingReservations", reservationService.getUserReservations(userId).stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED && !r.getReservationDate().isBefore(LocalDate.now())).toList());
        model.addAttribute("recentBooks", bookService.searchBooks(null, null, true, PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "id"))).getContent());
        return "student/dashboard";
    }

    @GetMapping("/catalogue")
    public String catalogue(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "category", required = false) Long categoryId,
                            @RequestParam(value = "onlyAvailable", defaultValue = "false") boolean onlyAvailable,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            @RequestParam(value = "size", defaultValue = "9") int size,
                            Model model) {
        Page<Book> bookPage = bookService.searchBooks(query, categoryId, onlyAvailable, PageRequest.of(page, size, Sort.by("title").ascending()));
        model.addAttribute("books", bookPage);
        model.addAttribute("categories", bookService.getAllCategories());
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("query", query);
        model.addAttribute("onlyAvailable", onlyAvailable);
        return "student/catalogue";
    }

    @GetMapping("/books/{id}")
    public String bookDetails(@PathVariable("id") Long id,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        Book book = bookService.findById(id);
        model.addAttribute("book", book);
        model.addAttribute("user", userDetails.getUser());
        return "student/book-details";
    }

    @PostMapping("/request-issue")
    public String requestIssue(@Valid @ModelAttribute("requestDto") IssueRequestDto dto,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid request parameters.");
            return "redirect:/student/catalogue";
        }
        try {
            circulationService.requestBookIssue(userDetails.getId(), dto.getBookId(), dto.getRemarks());
            redirectAttributes.addFlashAttribute("successMessage", "Book issue request submitted successfully! Library staff will review it.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/my-loans";
    }

    @PostMapping("/cancel-request/{id}")
    public String cancelRequest(@PathVariable("id") Long requestId,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        try {
            circulationService.cancelIssueRequest(requestId, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Issue request cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/my-loans";
    }

    @GetMapping("/my-loans")
    public String myLoans(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        Long userId = userDetails.getId();
        model.addAttribute("activeLoans", circulationService.getUserActiveLoans(userId));
        model.addAttribute("pendingRequests", circulationService.getUserRequests(userId));
        model.addAttribute("history", circulationService.getUserTransactions(userId, PageRequest.of(page, 10)));
        return "student/my-loans";
    }

    @GetMapping("/ai-assistant")
    public String aiAssistant() {
        return "student/ai-assistant";
    }

    @GetMapping("/semantic-search")
    public String semanticSearch() {
        return "student/semantic-search";
    }

    @GetMapping("/digital-reader")
    public String digitalReader(@RequestParam(value = "query", required = false) String query,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {
        Page<DigitalResource> resources = digitalResourceService.getAccessibleResources(query, userDetails.getRoleName(), PageRequest.of(page, 10));
        model.addAttribute("resources", resources);
        model.addAttribute("query", query);
        return "student/digital-reader";
    }

    @GetMapping("/digital-reader/view/{id}")
    public String viewDigitalDocument(@PathVariable("id") Long id,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      Model model) {
        DigitalResource resource = digitalResourceService.findById(id);
        ReadingProgress progress = digitalResourceService.getProgress(userDetails.getId(), id);
        model.addAttribute("resource", resource);
        model.addAttribute("lastPage", progress != null ? progress.getLastPage() : 1);
        return "student/pdf-viewer";
    }

    @GetMapping("/pomodoro")
    public String pomodoroTimer(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long userId = userDetails.getId();
        model.addAttribute("totalMinutes", studySessionService.getTotalStudyMinutes(userId));
        model.addAttribute("completedSessions", studySessionService.getCompletedSessionCount(userId));
        model.addAttribute("minutesLast7Days", studySessionService.getStudyMinutesLast7Days(userId));
        model.addAttribute("recentSessions", studySessionService.getUserSessions(userId, PageRequest.of(0, 10)).getContent());
        return "student/pomodoro";
    }

    @GetMapping("/seats")
    public String seatReservation(@RequestParam(value = "date", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        LocalDate selectedDate = (date != null && !date.isBefore(LocalDate.now())) ? date : LocalDate.now();
        List<LibrarySeat> seats = reservationService.getAllActiveSeats();
        Map<Long, Set<String>> bookedSlots = reservationService.getBookedSlotsForDate(selectedDate);

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("seats", seats);
        model.addAttribute("bookedSlots", bookedSlots);
        model.addAttribute("timeSlots", SeatReservationService.STANDARD_TIME_SLOTS);
        model.addAttribute("userReservations", reservationService.getUserReservations(userDetails.getId()));
        return "student/seats";
    }

    @PostMapping("/reserve-seat")
    public String reserveSeat(@Valid @ModelAttribute("reservationDto") SeatReservationDto dto,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid reservation details.");
            return "redirect:/student/seats";
        }
        try {
            reservationService.createReservation(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Library seat successfully reserved!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/seats?date=" + dto.getReservationDate();
    }

    @PostMapping("/cancel-reservation/{id}")
    public String cancelReservation(@PathVariable("id") Long reservationId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        try {
            reservationService.cancelReservation(reservationId, userDetails.getId(), false);
            redirectAttributes.addFlashAttribute("successMessage", "Seat reservation cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/seats";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User user = userService.findById(userDetails.getId());
        model.addAttribute("user", user);
        return "student/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @RequestParam("fullName") String fullName,
                                @RequestParam("email") String email,
                                @RequestParam("phone") String phone,
                                @RequestParam(value = "newPassword", required = false) String newPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            userService.updateUserProfile(userDetails.getUsername(), fullName, email, phone, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Profile details updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/profile";
    }
}
