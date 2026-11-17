package com.smartlibrary.config;

import com.smartlibrary.ai.VectorStoreService;
import com.smartlibrary.entity.*;
import com.smartlibrary.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final LibrarySeatRepository seatRepository;
    private final IssueRequestRepository issueRequestRepository;
    private final BookTransactionRepository transactionRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final DigitalResourceRepository digitalResourceRepository;
    private final PasswordEncoder passwordEncoder;
    private final VectorStoreService vectorStoreService;

    public DataInitializer(UserRepository userRepository,
                           BookRepository bookRepository,
                           CategoryRepository categoryRepository,
                           LibrarySeatRepository seatRepository,
                           IssueRequestRepository issueRequestRepository,
                           BookTransactionRepository transactionRepository,
                           SeatReservationRepository seatReservationRepository,
                           DigitalResourceRepository digitalResourceRepository,
                           PasswordEncoder passwordEncoder,
                           VectorStoreService vectorStoreService) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.seatRepository = seatRepository;
        this.issueRequestRepository = issueRequestRepository;
        this.transactionRepository = transactionRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.digitalResourceRepository = digitalResourceRepository;
        this.passwordEncoder = passwordEncoder;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting Smart AI Library System data verification and demo setup...");

        // 1. Initialize Users for all three portals
        User admin = initUser("admin", "admin123", "Chief Administrator", "admin@smartlibrary.com", Role.ROLE_ADMIN, "+1-555-0199");
        User staff = initUser("staff", "staff123", "Eleanor Vance (Librarian)", "staff@smartlibrary.com", Role.ROLE_STAFF, "+1-555-0122");
        User student = initUser("student", "student123", "Alex Morgan", "student@smartlibrary.com", Role.ROLE_STUDENT, "+1-555-0144");
        User student2 = initUser("student2", "student123", "Sarah Connor", "student2@smartlibrary.com", Role.ROLE_STUDENT, "+1-555-0177");

        // 2. Initialize Sample Demo Digital PDF Document if none exists
        initSampleDigitalDocument(admin);

        // 3. Build Vector Indexes for Books and Resources
        try {
            vectorStoreService.rebuildAllIndices();
            log.info("Initial semantic vector index rebuilt successfully.");
        } catch (Exception e) {
            log.warn("Vector indexing notice: {}", e.getMessage());
        }

        // 4. Initialize sample active transactions and issue requests for demonstration
        initSampleCirculationData(student, staff);

        log.info("Data verification complete. Ready for demo logins (admin/admin123, staff/staff123, student/student123).");
    }

    private User initUser(String username, String rawPassword, String fullName, String email, Role role, String phone) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setFullName(fullName);
            user.setEmail(email);
            user.setRole(role);
            user.setPhone(phone);
            user.setStatus("ACTIVE");
            User saved = userRepository.save(user);
            log.info("Created initial {} user: '{}' (Password: '{}')", role, username, rawPassword);
            return saved;
        });
    }

    private void initSampleDigitalDocument(User admin) {
        if (digitalResourceRepository.count() == 0) {
            try {
                Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(uploadDirPath);

                File samplePdf = uploadDirPath.resolve("ai_library_handbook.pdf").toFile();
                if (!samplePdf.exists()) {
                    try (PDDocument doc = new PDDocument()) {
                        PDPage page1 = new PDPage();
                        doc.addPage(page1);

                        try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                            cs.beginText();
                            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                            cs.newLineAtOffset(50, 720);
                            cs.showText("Smart AI Library - Digital Research Handbook");
                            cs.endText();

                            cs.beginText();
                            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                            cs.setLeading(18);
                            cs.newLineAtOffset(50, 680);
                            cs.showText("Welcome to the Smart AI Library System digital archive.");
                            cs.newLine();
                            cs.showText("This handbook provides guidance on automated catalogue queries,");
                            cs.newLine();
                            cs.showText("RAG-based semantic retrieval, and deep learning research materials.");
                            cs.newLine();
                            cs.showText("Library Members can browse physical and digital textbooks, reserve study carrels,");
                            cs.newLine();
                            cs.showText("and track Pomodoro study sessions directly in the portal.");
                            cs.endText();
                        }

                        doc.save(samplePdf);
                        log.info("Generated sample digital handbook PDF at: {}", samplePdf.getAbsolutePath());
                    }
                }

                DigitalResource resource = new DigitalResource();
                resource.setTitle("Smart AI Library Digital Research Handbook");
                resource.setAuthor("Library Academic Board");
                resource.setFileName("ai_library_handbook.pdf");
                resource.setFilePath(samplePdf.getAbsolutePath());
                resource.setFileSize(samplePdf.length());
                resource.setFileType("application/pdf");
                resource.setCategory(categoryRepository.findAll().stream().findFirst().orElse(null));
                resource.setDescription("Official orientation handbook covering digital library services, semantic search tips, and study timer usage.");
                resource.setAccessLevel("STUDENT");
                resource.setUploadedBy(admin);

                digitalResourceRepository.save(resource);
                log.info("Seeded initial digital resource: {}", resource.getTitle());
            } catch (IOException e) {
                log.error("Could not generate sample digital document: {}", e.getMessage());
            }
        }
    }

    private void initSampleCirculationData(User student, User staff) {
        if (transactionRepository.count() == 0 && bookRepository.count() > 0) {
            Book book1 = bookRepository.findByIsbn("978-0134685991").orElse(null); // Effective Java
            Book book2 = bookRepository.findByIsbn("978-0132350884").orElse(null); // Clean Code

            if (book1 != null && book1.getAvailableCopies() > 0) {
                // Issue Effective Java to student
                BookTransaction transaction = new BookTransaction();
                transaction.setUser(student);
                transaction.setBook(book1);
                transaction.setIssueDate(LocalDateTime.now().minusDays(5));
                transaction.setDueDate(LocalDateTime.now().plusDays(9));
                transaction.setStatus(TransactionStatus.ISSUED);
                transaction.setIssuedBy(staff);
                transaction.setRemarks("Standard student loan");
                transactionRepository.save(transaction);
                log.info("Seeded sample active loan: '{}' for student '{}'", book1.getTitle(), student.getUsername());
            }

            if (book2 != null) {
                // Pending issue request for Clean Code
                IssueRequest req = new IssueRequest(student, book2, "Required for Software Engineering project");
                issueRequestRepository.save(req);
                log.info("Seeded sample pending issue request: '{}' for student '{}'", book2.getTitle(), student.getUsername());
            }

            // Sample seat reservation for today
            LibrarySeat seat = seatRepository.findBySeatNumber("S-101").orElse(null);
            if (seat != null) {
                SeatReservation reservation = new SeatReservation(student, seat, LocalDate.now(), "09:00 - 12:00");
                seatReservationRepository.save(reservation);
                log.info("Seeded sample seat reservation for student '{}' on seat '{}'", student.getUsername(), seat.getSeatNumber());
            }
        }
    }
}
