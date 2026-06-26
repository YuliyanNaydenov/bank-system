package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordDTO {

    @NotBlank(message = "Текущата парола е задължителна")
    private String currentPassword;

    @NotBlank(message = "Новата парола е задължителна")
    @Size(min = 4, message = "Паролата трябва да е поне 4 символа")
    private String newPassword;

    @NotBlank(message = "Потвърждението е задължително")
    private String confirmPassword;
}
