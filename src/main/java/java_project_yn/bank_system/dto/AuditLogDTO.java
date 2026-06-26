package java_project_yn.bank_system.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private long id;
    private LocalDateTime timestamp;
    private String username;
    private String action;
    private String details;
}
