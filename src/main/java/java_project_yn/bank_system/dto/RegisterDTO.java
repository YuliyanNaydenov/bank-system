package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Самостоятелна регистрация на клиент (физическо лице).
 * Създава локален потребител с роля "client" + клиентски профил.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterDTO {

    @NotBlank(message = "Потребителското име е задължително")
    private String username;

    @NotBlank(message = "Паролата е задължителна")
    @Size(min = 4, message = "Паролата трябва да е поне 4 символа")
    private String password;

    @NotBlank(message = "Потвърждението на паролата е задължително")
    private String confirmPassword;

    @NotBlank(message = "Името е задължително")
    private String firstName;

    @NotBlank(message = "Фамилията е задължителна")
    private String lastName;

    @NotBlank(message = "ЕГН е задължително")
    @Pattern(regexp = "\\d{10}", message = "ЕГН трябва да съдържа точно 10 цифри")
    private String egn;
}
