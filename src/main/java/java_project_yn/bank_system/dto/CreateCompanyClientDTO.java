package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCompanyClientDTO {

    @NotBlank(message = "Името на фирмата е задължително")
    private String companyName;

    @NotBlank(message = "ЕИК е задължителен")
    private String eik;

    @NotBlank(message = "Представителят е задължителен")
    private String representativeName;

    /** Незадължително — потребителско име за вход на клиента. */
    private String username;
}
