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

    /** Отбелязва вноска като платена (без движение по сметка — напр. касово плащане). */
    CreditDTO payInstallment(long creditId, long installmentId);

    /** Плаща вноска чрез теглене от посочена сметка (атомарно). */
    CreditDTO payInstallmentFromAccount(long creditId, long installmentId, long accountId);

    /** Предсрочно погасяване — изплаща всички оставащи вноски наведнъж. */
    CreditDTO earlyPayoff(long creditId);

    /** Отказ / прекратяване на кредит (ако не е изплатен). */
    CreditDTO cancelCredit(long creditId);

    void deleteCredit(long id);
}
