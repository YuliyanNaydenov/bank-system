package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Създаване на нов служител (потребител с роля "employee") от администратор.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeDTO {

    @NotBlank(message = "Потребителското име е задължително")
    private String username;

    @NotBlank(message = "Паролата е задължителна")
    @Size(min = 4, message = "Паролата трябва да е поне 4 символа")
    private String password;

    @NotBlank(message = "Потвърждението на паролата е задължително")
    private String confirmPassword;
}
