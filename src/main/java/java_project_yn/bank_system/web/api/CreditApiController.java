package java_project_yn.bank_system.web.api;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/credits")
public class CreditApiController {

    private final CreditService creditService;

    @GetMapping
    public List<CreditDTO> getAll(Authentication authentication) {
        if (isClientOnly(authentication)) {
            return creditService.getCreditsByUsername(authentication.getName());
        }
        return creditService.getAllCredits();
    }

    @GetMapping("/{id}")
    public CreditDTO getById(@PathVariable long id) {
        return creditService.getCreditById(id);
    }

    /** Отпускане на кредит + генериране на погасителен план. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditDTO grant(@Valid @RequestBody CreateCreditDTO dto) {
        return creditService.grantCredit(dto);
    }

    /** Отбелязване на платена вноска. */
    @PostMapping("/{creditId}/installments/{installmentId}/pay")
    public CreditDTO payInstallment(@PathVariable long creditId, @PathVariable long installmentId) {
        return creditService.payInstallment(creditId, installmentId);
    }

    /** Плащане на вноска чрез теглене от сметка. */
    @PostMapping("/{creditId}/installments/{installmentId}/pay-from-account/{accountId}")
    public CreditDTO payInstallmentFromAccount(@PathVariable long creditId,
                                               @PathVariable long installmentId,
                                               @PathVariable long accountId) {
        return creditService.payInstallmentFromAccount(creditId, installmentId, accountId);
    }

    /** Предсрочно погасяване. */
    @PostMapping("/{id}/payoff")
    public CreditDTO payoff(@PathVariable long id) {
        return creditService.earlyPayoff(id);
    }

    /** Отказ на кредит. */
    @PostMapping("/{id}/cancel")
    public CreditDTO cancel(@PathVariable long id) {
        return creditService.cancelCredit(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        creditService.deleteCredit(id);
    }

    private boolean isClientOnly(Authentication auth) {
        boolean client = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("client"));
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("employee"));
        return client && !staff;
    }
}
