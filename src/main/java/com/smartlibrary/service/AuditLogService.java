package com.smartlibrary.service;

import com.smartlibrary.entity.AuditLog;
import com.smartlibrary.entity.User;
import com.smartlibrary.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User user, String username, String action, String entityName, String entityId, String details, HttpServletRequest request) {
        String ipAddress = "127.0.0.1";
        if (request != null) {
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader == null) {
                ipAddress = request.getRemoteAddr();
            } else {
                ipAddress = xfHeader.split(",")[0].trim();
            }
        }
        AuditLog auditLog = new AuditLog(user, username, action, entityName, entityId, details, ipAddress);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void log(String username, String action, String entityName, String entityId, String details) {
        AuditLog auditLog = new AuditLog(null, username, action, entityName, entityId, details, "SYSTEM");
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String query, Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            return auditLogRepository.searchLogs(query.trim(), pageable);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop20ByOrderByCreatedAtDesc();
    }
}
