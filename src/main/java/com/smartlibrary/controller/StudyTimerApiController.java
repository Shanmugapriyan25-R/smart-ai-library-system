package com.smartlibrary.controller;

import com.smartlibrary.dto.StudySessionDto;
import com.smartlibrary.entity.StudySession;
import com.smartlibrary.security.CustomUserDetails;
import com.smartlibrary.service.StudySessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/study-timer")
public class StudyTimerApiController {

    private final StudySessionService studySessionService;

    public StudyTimerApiController(StudySessionService studySessionService) {
        this.studySessionService = studySessionService;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> logSession(
            @Valid @RequestBody StudySessionDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        StudySession session = studySessionService.recordSession(userDetails.getId(), dto);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("sessionId", session.getId());
        resp.put("totalMinutes", studySessionService.getTotalStudyMinutes(userDetails.getId()));
        resp.put("sessionCount", studySessionService.getCompletedSessionCount(userDetails.getId()));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> stats = new HashMap<>();
        Long userId = userDetails.getId();
        stats.put("totalMinutes", studySessionService.getTotalStudyMinutes(userId));
        stats.put("sessionCount", studySessionService.getCompletedSessionCount(userId));
        stats.put("minutesLast7Days", studySessionService.getStudyMinutesLast7Days(userId));
        return ResponseEntity.ok(stats);
    }
}
