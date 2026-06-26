package java_project_yn.bank_system.dto;

import lombok.*;

/**
 * Една точка за графика — етикет и стойност.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartPointDTO {
    private String label;
    private long value;
}
