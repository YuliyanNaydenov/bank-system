package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;

import java.util.List;

public interface CreditService {
    List<CreditDTO> getAllCredits();
    CreditDTO getCreditById(long id);
    List<CreditDTO> getCreditsByClient(long clientId);
    List<CreditDTO> getCreditsByUsername(String username);

    /** Отпуска кредит и генерира анюитетен погасителен план. */
    CreditDTO grantCredit(CreateCreditDTO dto);

    /** Отбелязва вноска като платена; при последна вноска маркира кредита като изплатен. */
    CreditDTO payInstallment(long creditId, long installmentId);

    void deleteCredit(long id);
}
