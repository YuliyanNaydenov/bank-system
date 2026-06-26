package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.AuditLog;
import java_project_yn.bank_system.data.repo.AuditLogRepository;
import java_project_yn.bank_system.dto.AuditLogDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void log_savesEntry() {
        auditService.log("Тест", "детайли");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void log_neverThrows_evenIfRepoFails() {
        given(auditLogRepository.save(any(AuditLog.class))).willThrow(new RuntimeException("db down"));
        // Журналът не бива да чупи основното действие
        assertDoesNotThrow(() -> auditService.log("Тест", "детайли"));
    }

    @Test
    void getRecent_mapsToDto() {
        AuditLog log = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .username("admin")
                .action("Вход")
                .details("Успешно влизане")
                .build();
        log.setId(1L);
        given(auditLogRepository.findTop200ByOrderByTimestampDescIdDesc()).willReturn(List.of(log));

        List<AuditLogDTO> result = auditService.getRecent();

        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).getUsername());
        assertEquals("Вход", result.get(0).getAction());
    }

    @Test
    void clearAll_deletesThenLogsTheClear() {
        auditService.clearAll();
        verify(auditLogRepository).deleteAll();
        // след изтриването се записва един запис за самото изчистване
        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
