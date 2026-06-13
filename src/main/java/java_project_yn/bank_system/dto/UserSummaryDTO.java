package java_project_yn.bank_system.dto;

import lombok.*;

/**
 * Презентационен модел за потребител в админ панела.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDTO {
    private long id;
    private String username;
    private String roles;
    private boolean enabled;
}
