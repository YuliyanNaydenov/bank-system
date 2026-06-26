package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.AuditLog;
import java_project_yn.bank_system.data.repo.AuditLogRepository;
import java_project_yn.bank_system.dto.AuditLogDTO;
import java_project_yn.bank_system.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void log(String action, String details) {
        log(currentUsername(), action, details);
    }

    @Override
    public void log(String username, String action, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .username(username)
                    .action(action)
                    .details(details)
                    .build());
        } catch (Exception ignored) {
            // Журналът никога не бива да чупи основното действие
        }
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public List<AuditLogDTO> getRecent() {
        return auditLogRepository.findTop200ByOrderByTimestampDescIdDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void clearAll() {
        String admin = currentUsername();
        auditLogRepository.deleteAll();
        log(admin, "Изчистен журнал", "Всички предишни записи бяха изтрити");
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "система";
    }

    private AuditLogDTO toDto(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .username(log.getUsername())
                .action(log.getAction())
                .details(log.getDetails())
                .build();
    }
}
