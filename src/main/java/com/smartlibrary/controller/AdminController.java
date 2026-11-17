package com.smartlibrary.controller;

import com.smartlibrary.ai.VectorStoreService;
import com.smartlibrary.dto.BookDto;
import com.smartlibrary.dto.DigitalResourceDto;
import com.smartlibrary.dto.SystemSettingsFormDto;
import com.smartlibrary.dto.UserDto;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final BookService bookService;
    private final CirculationService circulationService;
    private final SeatReservationService reservationService;
    private final DigitalResourceService digitalResourceService;
    private final SystemSettingService settingService;
    private final AuditLogService auditLogService;
    private final AnalyticsService analyticsService;
    private final VectorStoreService vectorStoreService;

    public AdminController(UserService userService,
                           BookService bookService,
                           CirculationService circulationService,
                           SeatReservationService reservationService,
                           DigitalResourceService digitalResourceService,
                           SystemSettingService settingService,
                           AuditLogService auditLogService,
                           AnalyticsService analyticsService,
                           VectorStoreService vectorStoreService) {
        this.userService = userService;
        this.bookService = bookService;
        this.circulationService = circulationService;
        this.reservationService = reservationService;
        this.digitalResourceService = digitalResourceService;
        this.settingService = settingService;
        this.auditLogService = auditLogService;
        this.analyticsService = analyticsService;
        this.vectorStoreService = vectorStoreService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", analyticsService.getSystemSummary());
        model.addAttribute("todayIssues", analyticsService.getTodayIssuesCount());
        model.addAttribute("todayReturns", analyticsService.getTodayReturnsCount());
        model.addAttribute("recentLogs", auditLogService.getRecentLogs());
        model.addAttribute("overdueCount", circulationService.getOverdueTransactions(PageRequest.of(0, 5)).getTotalElements());
        return "admin/dashboard";
    }

    // ================= USER MANAGEMENT =================
    @GetMapping("/users")
    public String listUsers(@RequestParam(value = "role", required = false) String roleStr,
                            @RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        if (query != null && !query.isBlank()) {
            model.addAttribute("users", userService.searchUsers(query));
        } else if (roleStr != null && !roleStr.isBlank()) {
            Role role = Role.valueOf(roleStr);
            model.addAttribute("users", userService.findUsersByRole(role, PageRequest.of(page, 20)).getContent());
        } else {
            model.addAttribute("users", userService.findUsersByRole(Role.ROLE_STUDENT, PageRequest.of(page, 50)).getContent());
        }
        model.addAttribute("selectedRole", roleStr);
        model.addAttribute("query", query);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        if (!model.containsAttribute("userDto")) {
            model.addAttribute("userDto", new UserDto());
        }
        model.addAttribute("roles", Role.values());
        return "admin/user-create";
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute("userDto") UserDto userDto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "admin/user-create";
        }
        try {
            userService.createUserByAdmin(userDto, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "User account created successfully.");
            return "redirect:/admin/users";
        } catch (Exception e) {
            model.addAttribute("roles", Role.values());
            bindingResult.rejectValue("username", "error.userDto", e.getMessage());
            return "admin/user-create";
        }
    }

    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable("id") Long id, Model model) {
        User user = userService.findById(id);
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());

        model.addAttribute("userDto", dto);
        model.addAttribute("roles", Role.values());
        return "admin/user-edit";
    }

    @PostMapping("/users/edit/{id}")
    public String updateUser(@PathVariable("id") Long id,
                             @Valid @ModelAttribute("userDto") UserDto userDto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "admin/user-edit";
        }
        try {
            userService.updateUserByAdmin(id, userDto, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "User account updated successfully.");
            return "redirect:/admin/users";
        } catch (Exception e) {
            model.addAttribute("roles", Role.values());
            bindingResult.rejectValue("email", "error.userDto", e.getMessage());
            return "admin/user-edit";
        }
    }

    @PostMapping("/users/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        try {
            userService.toggleUserStatus(id, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "User account status updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ================= BOOK & CATALOGUE MANAGEMENT =================
    @GetMapping("/books")
    public String listBooks(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "category", required = false) Long categoryId,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        Page<Book> books = bookService.searchBooksForAdmin(query, categoryId, PageRequest.of(page, 15, Sort.by("id").descending()));
        model.addAttribute("books", books);
        model.addAttribute("categories", bookService.getAllCategories());
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("query", query);
        return "admin/books";
    }

    @GetMapping("/books/create")
    public String createBookForm(Model model) {
        if (!model.containsAttribute("bookDto")) {
            model.addAttribute("bookDto", new BookDto());
        }
        model.addAttribute("categories", bookService.getAllCategories());
        return "admin/book-create";
    }

    @PostMapping("/books/create")
    public String createBook(@Valid @ModelAttribute("bookDto") BookDto bookDto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", bookService.getAllCategories());
            return "admin/book-create";
        }
        try {
            Book book = bookService.createBook(bookDto, userDetails.getUsername());
            vectorStoreService.indexBook(book); // automatically vector index the new book
            redirectAttributes.addFlashAttribute("successMessage", "Book added and indexed into library catalogue successfully.");
            return "redirect:/admin/books";
        } catch (Exception e) {
            model.addAttribute("categories", bookService.getAllCategories());
            bindingResult.rejectValue("isbn", "error.bookDto", e.getMessage());
            return "admin/book-create";
        }
    }

    @GetMapping("/books/edit/{id}")
    public String editBookForm(@PathVariable("id") Long id, Model model) {
        Book book = bookService.findById(id);
        BookDto dto = new BookDto();
        dto.setId(book.getId());
        dto.setIsbn(book.getIsbn());
        dto.setTitle(book.getTitle());
        dto.setAuthor(book.getAuthor());
        dto.setCategoryId(book.getCategory().getId());
        dto.setPublisher(book.getPublisher());
        dto.setPublicationYear(book.getPublicationYear());
        dto.setEdition(book.getEdition());
        dto.setTotalCopies(book.getTotalCopies());
        dto.setAvailableCopies(book.getAvailableCopies());
        dto.setLocationShelf(book.getLocationShelf());
        dto.setDescription(book.getDescription());
        dto.setCoverImageUrl(book.getCoverImageUrl());
        dto.setIsArchived(book.getIsArchived());

        model.addAttribute("bookDto", dto);
        model.addAttribute("categories", bookService.getAllCategories());
        return "admin/book-edit";
    }

    @PostMapping("/books/edit/{id}")
    public String updateBook(@PathVariable("id") Long id,
                             @Valid @ModelAttribute("bookDto") BookDto bookDto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", bookService.getAllCategories());
            return "admin/book-edit";
        }
        try {
            Book book = bookService.updateBook(id, bookDto, userDetails.getUsername());
            vectorStoreService.indexBook(book);
            redirectAttributes.addFlashAttribute("successMessage", "Book updated successfully.");
            return "redirect:/admin/books";
        } catch (Exception e) {
            model.addAttribute("categories", bookService.getAllCategories());
            bindingResult.rejectValue("isbn", "error.bookDto", e.getMessage());
            return "admin/book-edit";
        }
    }

    @PostMapping("/books/archive/{id}")
    public String archiveBook(@PathVariable("id") Long id,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        try {
            bookService.archiveBook(id, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Book archived.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/books";
    }

    @PostMapping("/books/restore/{id}")
    public String restoreBook(@PathVariable("id") Long id,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        try {
            bookService.restoreBook(id, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Book restored to active catalogue.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/books";
    }

    // ================= CATEGORIES =================
    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", bookService.getAllCategories());
        return "admin/categories";
    }

    @PostMapping("/categories/create")
    public String createCategory(@RequestParam("name") String name,
                                 @RequestParam("code") String code,
                                 @RequestParam(value = "description", required = false) String description,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            bookService.createCategory(name, code, description, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Category '" + name + "' created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    // ================= DIGITAL RESOURCES =================
    @GetMapping("/digital-resources")
    public String digitalResources(Model model) {
        model.addAttribute("resources", digitalResourceService.getAccessibleResources(null, "ROLE_ADMIN", PageRequest.of(0, 50)).getContent());
        model.addAttribute("categories", bookService.getAllCategories());
        return "admin/digital-resources";
    }

    @PostMapping("/digital-resources/upload")
    public String uploadDigitalResource(@RequestParam("file") MultipartFile file,
                                        @Valid @ModelAttribute("resourceDto") DigitalResourceDto dto,
                                        BindingResult bindingResult,
                                        @AuthenticationPrincipal CustomUserDetails userDetails,
                                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid document information.");
            return "redirect:/admin/digital-resources";
        }
        try {
            DigitalResource resource = digitalResourceService.uploadPdf(file, dto, userDetails.getId());
            vectorStoreService.indexDigitalResource(resource);
            redirectAttributes.addFlashAttribute("successMessage", "PDF uploaded and indexed for RAG retrieval successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/digital-resources";
    }

    @PostMapping("/digital-resources/delete/{id}")
    public String deleteDigitalResource(@PathVariable("id") Long id,
                                        @AuthenticationPrincipal CustomUserDetails userDetails,
                                        RedirectAttributes redirectAttributes) {
        try {
            digitalResourceService.deleteResource(id, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Digital resource deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/digital-resources";
    }

    @PostMapping("/rebuild-index")
    public String rebuildVectorIndex(@AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        try {
            int count = vectorStoreService.rebuildAllIndices();
            auditLogService.log(userDetails.getUsername(), "REBUILD_VECTOR_INDEX", "VectorStore", "ALL", "Rebuilt indexes for " + count + " items");
            redirectAttributes.addFlashAttribute("successMessage", "Semantic vector search indexes successfully rebuilt for " + count + " items.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Index rebuild failed: " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    // ================= TRANSACTIONS & RESERVATIONS =================
    @GetMapping("/transactions")
    public String allTransactions(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        model.addAttribute("transactions", circulationService.getAllTransactions(PageRequest.of(page, 20, Sort.by("issueDate").descending())));
        return "admin/transactions";
    }

    @GetMapping("/reservations")
    public String allReservations(@RequestParam(value = "date", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                  Model model) {
        model.addAttribute("selectedDate", date);
        model.addAttribute("reservations", reservationService.getAllReservations(date, PageRequest.of(page, 20)));
        return "admin/reservations";
    }

    // ================= AUDIT LOGS =================
    @GetMapping("/audit-logs")
    public String auditLogs(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        model.addAttribute("logs", auditLogService.getAuditLogs(query, PageRequest.of(page, 25)));
        model.addAttribute("query", query);
        return "admin/audit-logs";
    }

    // ================= SYSTEM SETTINGS & AI CONFIGURATION =================
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settingsForm", settingService.getSettingsForm());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@ModelAttribute("settingsForm") SystemSettingsFormDto form,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            settingService.saveSettingsForm(form);
            auditLogService.log(userDetails.getUsername(), "UPDATE_SETTINGS", "SystemSetting", "GLOBAL", "Updated library circulation & AI configurations");
            redirectAttributes.addFlashAttribute("successMessage", "System settings and AI configuration updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
