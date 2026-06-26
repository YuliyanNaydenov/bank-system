package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop200ByOrderByTimestampDescIdDesc();
}
