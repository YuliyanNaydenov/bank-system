package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIndividualClientDTO {

    @NotBlank(message = "Името е задължително")
    private String firstName;

    @NotBlank(message = "Фамилията е задължителна")
    private String lastName;

    @NotBlank(message = "ЕГН е задължително")
    @Pattern(regexp = "\\d{10}", message = "ЕГН трябва да съдържа точно 10 цифри")
    private String egn;

    /** Незадължително — потребителско име за вход на клиента. */
    private String username;
}
