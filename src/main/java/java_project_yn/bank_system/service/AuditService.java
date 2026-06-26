package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.AuditLogDTO;

import java.util.List;

public interface AuditService {
    /** Записва действие, като взема текущия потребител от контекста. */
    void log(String action, String details);

    /** Записва действие за конкретен потребител (напр. при вход). */
    void log(String username, String action, String details);

    /** Последните записи (за admin прегледа). */
    List<AuditLogDTO> getRecent();

    /** Изтрива всички записи (оставя един запис за самото изчистване). */
    void clearAll();
}
