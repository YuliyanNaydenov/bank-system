package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.RegisterDTO;

public interface RegistrationService {
    /** Регистрира нов клиент (физическо лице) с локален акаунт и роля "client". */
    void registerClient(RegisterDTO dto);
}
