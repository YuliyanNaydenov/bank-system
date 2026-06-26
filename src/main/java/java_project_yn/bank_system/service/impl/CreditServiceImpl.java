package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.*;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.data.repo.CreditTypeRepository;
import java_project_yn.bank_system.data.repo.InstallmentRepository;
import java_project_yn.bank_system.data.repo.TransactionRepository;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.dto.InstallmentDTO;
import java_project_yn.bank_system.exception.AccountNotFoundException;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.exception.CreditNotFoundException;
import java_project_yn.bank_system.exception.CreditTypeNotFoundException;
import java_project_yn.bank_system.exception.InstallmentNotFoundException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.CreditService;
import java_project_yn.bank_system.util.AnnuityCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditRepository creditRepository;
    private final CreditTypeRepository creditTypeRepository;
    private final ClientRepository clientRepository;
    private final InstallmentRepository installmentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public List<CreditDTO> getAllCredits() {
        return creditRepository.findAll().stream()
                .map(c -> toDto(c, false)).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public CreditDTO getCreditById(long id) {
        return toDto(getEntity(id), true);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public List<CreditDTO> getCreditsByClient(long clientId) {
        return creditRepository.findByClient_Id(clientId).stream()
                .map(c -> toDto(c, false)).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<CreditDTO> getCreditsByUsername(String username) {
        return creditRepository.findByClient_Username(username).stream()
                .map(c -> toDto(c, false)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public CreditDTO grantCredit(CreateCreditDTO dto) {
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new ClientNotFoundException(
                        "Клиент с id=" + dto.getClientId() + " не е намерен!"));
        CreditType type = creditTypeRepository.findById(dto.getCreditTypeId())
                .orElseThrow(() -> new CreditTypeNotFoundException(
                        "Кредитен вид с id=" + dto.getCreditTypeId() + " не е намерен!"));

        // ── Бизнес валидации спрямо параметрите на вида кредит ──────────────
        if (dto.getAmount().signum() <= 0) {
            throw new BusinessRuleException("Сумата на кредита трябва да е положителна!");
        }
        if (dto.getAmount().compareTo(type.getMaxAmount()) > 0) {
            throw new BusinessRuleException("Сумата надвишава максималната за този вид кредит ("
                    + type.getMaxAmount() + ")!");
        }
        if (dto.getTermMonths() < 1) {
            throw new BusinessRuleException("Срокът трябва да е поне 1 месец!");
        }
        if (dto.getTermMonths() > type.getMaxTermMonths()) {
            throw new BusinessRuleException("Срокът надвишава максималния за този вид кредит ("
                    + type.getMaxTermMonths() + " месеца)!");
        }

        Credit credit = Credit.builder()
                .client(client)
                .creditType(type)
                .amount(dto.getAmount())
                .termMonths(dto.getTermMonths())
                .startDate(LocalDate.now())
                .status(CreditStatus.ACTIVE)
                .installments(new ArrayList<>())
                .build();

        // ── Генериране на анюитетния погасителен план ───────────────────────
        List<AnnuityCalculator.ScheduleRow> schedule = AnnuityCalculator.generate(
                dto.getAmount(), type.getAnnualInterestRate(), dto.getTermMonths());

        for (AnnuityCalculator.ScheduleRow row : schedule) {
            Installment installment = Installment.builder()
                    .credit(credit)
                    .monthNumber(row.monthNumber())
                    .dueDate(credit.getStartDate().plusMonths(row.monthNumber()))
                    .paymentAmount(row.payment())
                    .principalPart(row.principal())
                    .interestPart(row.interest())
                    .remainingBalance(row.remaining())
                    .paid(false)
                    .build();
            credit.getInstallments().add(installment);
        }

        CreditDTO saved = toDto(creditRepository.save(credit), true);
        auditService.log("Отпуснат кредит", "Кредит #" + saved.getId() + " на " + saved.getClientName()
                + " — " + saved.getAmount() + " € за " + saved.getTermMonths() + " мес.");
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public CreditDTO payInstallment(long creditId, long installmentId) {
        Credit credit = getEntity(creditId);
        Installment installment = getPayableInstallment(credit, installmentId);

        installment.setPaid(true);
        markPaidIfComplete(credit);

        CreditDTO saved = toDto(creditRepository.save(credit), true);
        auditService.log("Платена вноска", "Вноска #" + installment.getMonthNumber()
                + " по кредит #" + credit.getId() + " (" + saved.getClientName() + ")");
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee', 'client')")
    public CreditDTO payInstallmentFromAccount(long creditId, long installmentId, long accountId) {
        Credit credit = getEntity(creditId);
        Installment installment = getPayableInstallment(credit, installmentId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Сметка с id=" + accountId + " не е намерена!"));

        ensureClientAccess(credit, account);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Сметката е закрита!");
        }
        BigDecimal amount = installment.getPaymentAmount();
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException("Недостатъчна наличност по сметката!");
        }

        // Теглене + запис на транзакция
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        transactionRepository.save(Transaction.builder()
                .account(account)
                .type(TransactionType.CREDIT_PAYMENT)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .balanceAfter(account.getBalance())
                .description("Вноска #" + installment.getMonthNumber() + " по кредит #" + credit.getId())
                .build());

        installment.setPaid(true);
        markPaidIfComplete(credit);

        CreditDTO saved = toDto(creditRepository.save(credit), true);
        auditService.log("Погасена вноска от сметка", "Вноска #" + installment.getMonthNumber()
                + " по кредит #" + credit.getId() + " от сметка " + account.getIban());
        return saved;
    }

    // ── Помощни методи за плащане ───────────────────────────────────────────

    private Installment getPayableInstallment(Credit credit, long installmentId) {
        Installment installment = credit.getInstallments().stream()
                .filter(i -> i.getId() == installmentId)
                .findFirst()
                .orElseThrow(() -> new InstallmentNotFoundException(
                        "Вноска с id=" + installmentId + " не е намерена за този кредит!"));
        if (installment.isPaid()) {
            throw new BusinessRuleException("Вноската вече е платена!");
        }
        boolean hasUnpaidEarlier = credit.getInstallments().stream()
                .anyMatch(i -> i.getMonthNumber() < installment.getMonthNumber() && !i.isPaid());
        if (hasUnpaidEarlier) {
            throw new BusinessRuleException("Първо платете предходните неплатени вноски!");
        }
        return installment;
    }

    private void markPaidIfComplete(Credit credit) {
        if (credit.getInstallments().stream().allMatch(Installment::isPaid)) {
            credit.setStatus(CreditStatus.PAID);
        }
    }

    /** Ако операцията е от клиент — и кредитът, и сметката трябва да са негови. */
    private void ensureClientAccess(Credit credit, Account account) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Неоторизиран достъп!");
        }
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("employee"));
        if (staff) {
            return;
        }
        String username = auth.getName();
        boolean ownsCredit = credit.getClient() != null && username.equals(credit.getClient().getUsername());
        boolean ownsAccount = account.getOwner() != null && username.equals(account.getOwner().getUsername());
        if (!ownsCredit || !ownsAccount) {
            throw new AccessDeniedException("Нямате достъп до този кредит или сметка!");
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public CreditDTO earlyPayoff(long creditId) {
        Credit credit = getEntity(creditId);
        if (credit.getStatus() != CreditStatus.ACTIVE) {
            throw new BusinessRuleException("Само активен кредит може да се погаси предсрочно!");
        }
        credit.getInstallments().forEach(i -> i.setPaid(true));
        credit.setStatus(CreditStatus.PAID);
        CreditDTO saved = toDto(creditRepository.save(credit), true);
        auditService.log("Предсрочно погасяване", "Кредит #" + credit.getId() + " (" + saved.getClientName() + ")");
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public CreditDTO cancelCredit(long creditId) {
        Credit credit = getEntity(creditId);
        if (credit.getStatus() == CreditStatus.PAID) {
            throw new BusinessRuleException("Изплатен кредит не може да бъде отказан!");
        }
        if (credit.getStatus() == CreditStatus.CANCELLED) {
            throw new BusinessRuleException("Кредитът вече е отказан!");
        }
        boolean hasPaidInstallment = credit.getInstallments().stream().anyMatch(Installment::isPaid);
        if (hasPaidInstallment) {
            throw new BusinessRuleException(
                    "Кредит с вече платени вноски не може да бъде отказан. Използвайте предсрочно погасяване.");
        }
        credit.setStatus(CreditStatus.CANCELLED);
        CreditDTO saved = toDto(creditRepository.save(credit), true);
        auditService.log("Отказан кредит", "Кредит #" + credit.getId() + " (" + saved.getClientName() + ")");
        return saved;
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteCredit(long id) {
        if (!creditRepository.existsById(id)) {
            throw new CreditNotFoundException("Кредит с id=" + id + " не е намерен!");
        }
        creditRepository.deleteById(id);
        auditService.log("Изтрит кредит", "Кредит id=" + id);
    }

    // ── Помощни методи ──────────────────────────────────────────────────────

    private Credit getEntity(long id) {
        return creditRepository.findById(id)
                .orElseThrow(() -> new CreditNotFoundException("Кредит с id=" + id + " не е намерен!"));
    }

    private CreditDTO toDto(Credit credit, boolean includeSchedule) {
        List<Installment> installments = credit.getInstallments();

        BigDecimal monthlyPayment = installments.isEmpty()
                ? BigDecimal.ZERO
                : installments.get(0).getPaymentAmount();

        BigDecimal totalToRepay = installments.stream()
                .map(Installment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = installments.stream()
                .filter(i -> !i.isPaid())
                .map(i -> i.getPrincipalPart().add(i.getInterestPart()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int paidCount = (int) installments.stream().filter(Installment::isPaid).count();

        LocalDate today = LocalDate.now();
        boolean hasOverdue = credit.getStatus() == CreditStatus.ACTIVE
                && installments.stream().anyMatch(i -> !i.isPaid()
                        && i.getDueDate() != null && i.getDueDate().isBefore(today));

        List<InstallmentDTO> installmentDtos = null;
        if (includeSchedule) {
            installmentDtos = installments.stream()
                    .sorted((a, b) -> Integer.compare(a.getMonthNumber(), b.getMonthNumber()))
                    .map(this::toInstallmentDto)
                    .collect(Collectors.toList());
        }

        return CreditDTO.builder()
                .id(credit.getId())
                .clientId(credit.getClient().getId())
                .clientName(credit.getClient().getDisplayName())
                .creditTypeId(credit.getCreditType().getId())
                .creditTypeName(credit.getCreditType().getName())
                .annualInterestRate(credit.getCreditType().getAnnualInterestRate())
                .amount(credit.getAmount())
                .termMonths(credit.getTermMonths())
                .startDate(credit.getStartDate())
                .status(credit.getStatus().name())
                .monthlyPayment(monthlyPayment)
                .totalToRepay(totalToRepay)
                .remainingBalance(remaining)
                .paidInstallments(paidCount)
                .hasOverdue(hasOverdue)
                .installments(installmentDtos)
                .build();
    }

    private InstallmentDTO toInstallmentDto(Installment i) {
        boolean overdue = !i.isPaid() && i.getDueDate() != null
                && i.getDueDate().isBefore(LocalDate.now());
        return InstallmentDTO.builder()
                .id(i.getId())
                .monthNumber(i.getMonthNumber())
                .dueDate(i.getDueDate())
                .paymentAmount(i.getPaymentAmount())
                .principalPart(i.getPrincipalPart())
                .interestPart(i.getInterestPart())
                .remainingBalance(i.getRemainingBalance())
                .paid(i.isPaid())
                .overdue(overdue)
                .build();
    }
}
