package com.smartlibrary.service;

import com.smartlibrary.dto.StudySessionDto;
import com.smartlibrary.entity.StudySession;
import com.smartlibrary.entity.User;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.repository.StudySessionRepository;
import com.smartlibrary.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public StudySessionService(StudySessionRepository studySessionRepository,
                               UserRepository userRepository,
                               AuditLogService auditLogService) {
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public StudySession recordSession(Long userId, StudySessionDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        StudySession session = new StudySession(user, dto.getDurationMinutes(), dto.getSessionType(), dto.getNotes());
        StudySession saved = studySessionRepository.save(session);

        auditLogService.log(user.getUsername(), "POMODORO_SESSION_LOGGED", "StudySession",
                saved.getId().toString(), "Completed " + dto.getDurationMinutes() + " min " + dto.getSessionType() + " study session");

        return saved;
    }

    @Transactional(readOnly = true)
    public List<StudySession> getUserSessions(Long userId) {
        return studySessionRepository.findByUserIdOrderByCompletedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<StudySession> getUserSessions(Long userId, Pageable pageable) {
        return studySessionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getTotalStudyMinutes(Long userId) {
        return studySessionRepository.sumStudyMinutesByUser(userId);
    }

    @Transactional(readOnly = true)
    public long getCompletedSessionCount(Long userId) {
        return studySessionRepository.countCompletedSessionsByUser(userId);
    }

    @Transactional(readOnly = true)
    public long getStudyMinutesLast7Days(Long userId) {
        return studySessionRepository.sumStudyMinutesByUserSince(userId, LocalDateTime.now().minusDays(7));
    }
}
