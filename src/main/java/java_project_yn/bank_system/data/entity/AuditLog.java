package java_project_yn.bank_system.data.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запис в журнала за действия — кой потребител, какво действие, кога и детайли.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", length = 100, nullable = false)
    private String action;

    @Column(name = "details", length = 1000)
    private String details;
}
