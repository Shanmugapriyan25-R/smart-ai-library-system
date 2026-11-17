package com.smartlibrary.service;

import com.smartlibrary.dto.DigitalResourceDto;
import com.smartlibrary.entity.Category;
import com.smartlibrary.entity.DigitalResource;
import com.smartlibrary.entity.ReadingProgress;
import com.smartlibrary.entity.User;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.exception.UnauthorizedActionException;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.repository.DigitalResourceRepository;
import com.smartlibrary.repository.ReadingProgressRepository;
import com.smartlibrary.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DigitalResourceService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final DigitalResourceRepository resourceRepository;
    private final ReadingProgressRepository progressRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public DigitalResourceService(DigitalResourceRepository resourceRepository,
                                  ReadingProgressRepository progressRepository,
                                  CategoryRepository categoryRepository,
                                  UserRepository userRepository,
                                  AuditLogService auditLogService) {
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public DigitalResource findById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Digital resource not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<DigitalResource> getAccessibleResources(String query, String userRole, Pageable pageable) {
        List<String> allowedLevels;
        if ("ROLE_ADMIN".equals(userRole)) {
            allowedLevels = List.of("STUDENT", "STAFF", "ADMIN");
        } else if ("ROLE_STAFF".equals(userRole)) {
            allowedLevels = List.of("STUDENT", "STAFF");
        } else {
            allowedLevels = List.of("STUDENT");
        }
        return resourceRepository.searchAccessibleResources(query, allowedLevels, pageable);
    }

    public boolean canUserAccess(DigitalResource resource, String userRole) {
        if ("ROLE_ADMIN".equals(userRole)) return true;
        if ("ROLE_STAFF".equals(userRole)) {
            return !"ADMIN".equalsIgnoreCase(resource.getAccessLevel());
        }
        return "STUDENT".equalsIgnoreCase(resource.getAccessLevel());
    }

    @Transactional
    public DigitalResource uploadPdf(MultipartFile file, DigitalResourceDto dto, Long uploaderUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please select a file to upload.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessRuleException("Invalid document format. Only PDF files are supported by the Digital Document Reader.");
        }

        User uploader = userRepository.findById(uploaderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Uploader user not found with ID: " + uploaderUserId));

        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
        }

        try {
            Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String storedFileName = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path targetPath = targetDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            DigitalResource resource = new DigitalResource();
            resource.setTitle(dto.getTitle().trim());
            resource.setAuthor(dto.getAuthor());
            resource.setFileName(originalName);
            resource.setFilePath(targetPath.toString());
            resource.setFileSize(file.getSize());
            resource.setFileType("application/pdf");
            resource.setCategory(category);
            resource.setDescription(dto.getDescription());
            resource.setAccessLevel(dto.getAccessLevel() != null ? dto.getAccessLevel().toUpperCase() : "STUDENT");
            resource.setUploadedBy(uploader);

            DigitalResource saved = resourceRepository.save(resource);
            auditLogService.log(uploader.getUsername(), "UPLOAD_DIGITAL_RESOURCE", "DigitalResource",
                    saved.getId().toString(), "Uploaded PDF: " + saved.getTitle());
            return saved;
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store uploaded document: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteResource(Long id, String adminUsername) {
        DigitalResource resource = findById(id);
        try {
            Path path = Paths.get(resource.getFilePath());
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}

        resourceRepository.delete(resource);
        auditLogService.log(adminUsername, "DELETE_DIGITAL_RESOURCE", "DigitalResource", id.toString(),
                "Deleted resource: " + resource.getTitle());
    }

    public Resource loadFileAsResource(Long resourceId, String userRole) {
        DigitalResource resource = findById(resourceId);
        if (!canUserAccess(resource, userRole)) {
            throw new UnauthorizedActionException("You do not have permission to view this digital document.");
        }

        try {
            Path filePath = Paths.get(resource.getFilePath()).normalize();
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                Path fallback = Paths.get(uploadDir).resolve(resource.getFileName()).normalize();
                if (Files.exists(fallback) && Files.isReadable(fallback)) {
                    filePath = fallback;
                } else {
                    Path fallback2 = Paths.get("src/main/resources/data").resolve(resource.getFileName()).normalize();
                    if (Files.exists(fallback2) && Files.isReadable(fallback2)) {
                        filePath = fallback2;
                    }
                }
            }
            Resource res = new UrlResource(filePath.toUri());
            if (res.exists() && res.isReadable()) {
                return res;
            } else {
                throw new ResourceNotFoundException("Document file not found on disk: " + resource.getFileName());
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Malformed document file path for resource ID: " + resourceId);
        }
    }

    @Transactional
    public ReadingProgress saveProgress(Long userId, Long resourceId, int lastPage, int totalPages, String notes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        DigitalResource resource = findById(resourceId);

        ReadingProgress progress = progressRepository.findByUserIdAndDigitalResourceId(userId, resourceId)
                .orElse(new ReadingProgress(user, resource, lastPage, totalPages));

        progress.setLastPage(lastPage);
        progress.setTotalPages(totalPages);
        if (notes != null) {
            progress.setNotes(notes);
        }
        progress.setLastReadAt(LocalDateTime.now());
        return progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public ReadingProgress getProgress(Long userId, Long resourceId) {
        return progressRepository.findByUserIdAndDigitalResourceId(userId, resourceId)
                .orElse(null);
    }
}
