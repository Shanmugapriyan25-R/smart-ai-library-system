package com.smartlibrary.controller;

import com.smartlibrary.entity.ReadingProgress;
import com.smartlibrary.security.CustomUserDetails;
import com.smartlibrary.service.DigitalResourceService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/digital-reader")
public class DigitalResourceApiController {

    private final DigitalResourceService digitalResourceService;

    public DigitalResourceApiController(DigitalResourceService digitalResourceService) {
        this.digitalResourceService = digitalResourceService;
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> streamPdf(@PathVariable("id") Long id,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        Resource file = digitalResourceService.loadFileAsResource(id, userDetails.getRoleName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    @PostMapping("/progress")
    public ResponseEntity<Map<String, Object>> saveReadingProgress(
            @RequestParam("resourceId") Long resourceId,
            @RequestParam("lastPage") int lastPage,
            @RequestParam("totalPages") int totalPages,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReadingProgress progress = digitalResourceService.saveProgress(userDetails.getId(), resourceId, lastPage, totalPages, notes);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("lastPage", progress.getLastPage());
        resp.put("progressPercent", progress.getProgressPercent());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/progress/{id}")
    public ResponseEntity<Map<String, Object>> getReadingProgress(
            @PathVariable("id") Long resourceId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReadingProgress progress = digitalResourceService.getProgress(userDetails.getId(), resourceId);
        Map<String, Object> resp = new HashMap<>();
        if (progress != null) {
            resp.put("lastPage", progress.getLastPage());
            resp.put("totalPages", progress.getTotalPages());
            resp.put("progressPercent", progress.getProgressPercent());
            resp.put("notes", progress.getNotes());
        } else {
            resp.put("lastPage", 1);
            resp.put("totalPages", 1);
            resp.put("progressPercent", 0);
        }
        return ResponseEntity.ok(resp);
    }
}
