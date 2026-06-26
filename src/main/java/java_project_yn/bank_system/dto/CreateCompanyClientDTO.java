package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "\\d{9}|\\d{13}", message = "ЕИК трябва да е 9 или 13 цифри")
    private String eik;

    @NotBlank(message = "Представителят е задължителен")
    private String representativeName;

    /** Незадължително — потребителско име за вход на клиента. */
    private String username;

    /** Незадължително — парола; ако е попълнена заедно с потребителско име, се създава вход. */
    private String password;
}
