package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.*;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.data.repo.CreditTypeRepository;
import java_project_yn.bank_system.data.repo.InstallmentRepository;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.dto.InstallmentDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.exception.CreditNotFoundException;
import java_project_yn.bank_system.exception.CreditTypeNotFoundException;
import java_project_yn.bank_system.exception.InstallmentNotFoundException;
import java_project_yn.bank_system.service.CreditService;
import java_project_yn.bank_system.util.AnnuityCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
                    .paymentAmount(row.payment())
                    .principalPart(row.principal())
                    .interestPart(row.interest())
                    .remainingBalance(row.remaining())
                    .paid(false)
                    .build();
            credit.getInstallments().add(installment);
        }

        return toDto(creditRepository.save(credit), true);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public CreditDTO payInstallment(long creditId, long installmentId) {
        Credit credit = getEntity(creditId);
        Installment installment = credit.getInstallments().stream()
                .filter(i -> i.getId() == installmentId)
                .findFirst()
                .orElseThrow(() -> new InstallmentNotFoundException(
                        "Вноска с id=" + installmentId + " не е намерена за този кредит!"));

        if (installment.isPaid()) {
            throw new BusinessRuleException("Вноската вече е платена!");
        }

        // Вноските се плащат последователно
        boolean hasUnpaidEarlier = credit.getInstallments().stream()
                .anyMatch(i -> i.getMonthNumber() < installment.getMonthNumber() && !i.isPaid());
        if (hasUnpaidEarlier) {
            throw new BusinessRuleException("Първо платете предходните неплатени вноски!");
        }

        installment.setPaid(true);

        boolean allPaid = credit.getInstallments().stream().allMatch(Installment::isPaid);
        if (allPaid) {
            credit.setStatus(CreditStatus.PAID);
        }

        return toDto(creditRepository.save(credit), true);
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteCredit(long id) {
        if (!creditRepository.existsById(id)) {
            throw new CreditNotFoundException("Кредит с id=" + id + " не е намерен!");
        }
        creditRepository.deleteById(id);
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
                .installments(installmentDtos)
                .build();
    }

    private InstallmentDTO toInstallmentDto(Installment i) {
        return InstallmentDTO.builder()
                .id(i.getId())
                .monthNumber(i.getMonthNumber())
                .paymentAmount(i.getPaymentAmount())
                .principalPart(i.getPrincipalPart())
                .interestPart(i.getInterestPart())
                .remainingBalance(i.getRemainingBalance())
                .paid(i.isPaid())
                .build();
    }
}
