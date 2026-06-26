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
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock private CreditRepository creditRepository;
    @Mock private CreditTypeRepository creditTypeRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private CreditServiceImpl creditService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateStaff() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("emp", "x",
                        List.of(new SimpleGrantedAuthority("employee"))));
    }

    private Account accountWith(String balance) {
        Account a = Account.builder()
                .iban("BG1").balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE).owner(client).build();
        a.setId(10L);
        return a;
    }

    private IndividualClient client;
    private CreditType consumerType;

    @BeforeEach
    void setUp() {
        client = new IndividualClient();
        client.setFirstName("Иван");
        client.setLastName("Петров");
        client.setEgn("8001011234");

        consumerType = CreditType.builder()
                .name("Потребителски")
                .annualInterestRate(new BigDecimal("8.5"))
                .maxAmount(new BigDecimal("50000"))
                .maxTermMonths(84)
                .build();
        consumerType.setId(1L);
    }

    @Test
    void grantCredit_generatesSchedule() {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("10000")).termMonths(12).build();
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        given(creditTypeRepository.findById(1L)).willReturn(Optional.of(consumerType));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.grantCredit(dto);

        assertEquals(12, result.getInstallments().size());
        assertEquals(CreditStatus.ACTIVE.name(), result.getStatus());
        assertEquals(new BigDecimal("872.20"), result.getMonthlyPayment());
        assertEquals(0, result.getPaidInstallments());
    }

    @Test
    void grantCredit_amountOverMax_throws() {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("60000")).termMonths(12).build();
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        given(creditTypeRepository.findById(1L)).willReturn(Optional.of(consumerType));

        assertThrows(BusinessRuleException.class, () -> creditService.grantCredit(dto));
    }

    @Test
    void grantCredit_termOverMax_throws() {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("10000")).termMonths(120).build();
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        given(creditTypeRepository.findById(1L)).willReturn(Optional.of(consumerType));

        assertThrows(BusinessRuleException.class, () -> creditService.grantCredit(dto));
    }

    @Test
    void payInstallment_outOfOrder_throws() {
        Credit credit = buildCreditWithTwoInstallments();
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        // Опит за плащане на вноска №2 преди №1
        assertThrows(BusinessRuleException.class,
                () -> creditService.payInstallment(1L, 102L));
    }

    @Test
    void payInstallment_lastOne_marksCreditPaid() {
        Credit credit = buildCreditWithTwoInstallments();
        credit.getInstallments().get(0).setPaid(true); // първата вече е платена
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.payInstallment(1L, 102L);

        assertEquals(CreditStatus.PAID.name(), result.getStatus());
        assertEquals(2, result.getPaidInstallments());
    }

    @Test
    void grantCredit_setsDueDates() {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("10000")).termMonths(12).build();
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        given(creditTypeRepository.findById(1L)).willReturn(Optional.of(consumerType));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.grantCredit(dto);

        assertNotNull(result.getInstallments().get(0).getDueDate());
        assertFalse(result.isHasOverdue());
    }

    @Test
    void earlyPayoff_marksAllPaidAndStatusPaid() {
        Credit credit = buildCreditWithTwoInstallments();
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.earlyPayoff(1L);

        assertEquals(CreditStatus.PAID.name(), result.getStatus());
        assertEquals(2, result.getPaidInstallments());
    }

    @Test
    void cancelCredit_withPaidInstallment_throws() {
        Credit credit = buildCreditWithTwoInstallments();
        credit.getInstallments().get(0).setPaid(true);
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        assertThrows(BusinessRuleException.class, () -> creditService.cancelCredit(1L));
    }

    @Test
    void cancelCredit_noPaidInstallments_setsCancelled() {
        Credit credit = buildCreditWithTwoInstallments();
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.cancelCredit(1L);

        assertEquals(CreditStatus.CANCELLED.name(), result.getStatus());
    }

    @Test
    void payFromAccount_debitsAccountAndMarksPaid() {
        authenticateStaff();
        Credit credit = buildCreditWithTwoInstallments();
        Account account = accountWith("600.00");
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(accountRepository.findById(10L)).willReturn(Optional.of(account));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.payInstallmentFromAccount(1L, 101L, 10L);

        assertEquals(0, account.getBalance().compareTo(new BigDecimal("90.00")));
        assertTrue(credit.getInstallments().get(0).isPaid());
        assertEquals(1, result.getPaidInstallments());
        verify(transactionRepository).save(any());
    }

    @Test
    void payFromAccount_insufficientFunds_throws() {
        authenticateStaff();
        Credit credit = buildCreditWithTwoInstallments();
        Account account = accountWith("100.00");
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(accountRepository.findById(10L)).willReturn(Optional.of(account));

        assertThrows(BusinessRuleException.class,
                () -> creditService.payInstallmentFromAccount(1L, 101L, 10L));
    }

    @Test
    void payFromAccount_closedAccount_throws() {
        authenticateStaff();
        Credit credit = buildCreditWithTwoInstallments();
        Account account = accountWith("600.00");
        account.setStatus(AccountStatus.CLOSED);
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(accountRepository.findById(10L)).willReturn(Optional.of(account));

        assertThrows(BusinessRuleException.class,
                () -> creditService.payInstallmentFromAccount(1L, 101L, 10L));
    }

    @Test
    void payFromAccount_lastInstallment_marksCreditPaid() {
        authenticateStaff();
        Credit credit = buildCreditWithTwoInstallments();
        credit.getInstallments().get(0).setPaid(true);
        Account account = accountWith("600.00");
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));
        given(accountRepository.findById(10L)).willReturn(Optional.of(account));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(Credit.class))).willAnswer(inv -> inv.getArgument(0));

        CreditDTO result = creditService.payInstallmentFromAccount(1L, 102L, 10L);

        assertEquals(CreditStatus.PAID.name(), result.getStatus());
    }

    private Credit buildCreditWithTwoInstallments() {
        Credit credit = Credit.builder()
                .client(client).creditType(consumerType)
                .amount(new BigDecimal("1000")).termMonths(2)
                .status(CreditStatus.ACTIVE)
                .build();
        credit.setId(1L);

        Installment i1 = Installment.builder().credit(credit).monthNumber(1)
                .paymentAmount(new BigDecimal("510.00")).principalPart(new BigDecimal("500.00"))
                .interestPart(new BigDecimal("10.00")).remainingBalance(new BigDecimal("500.00"))
                .paid(false).build();
        i1.setId(101L);
        Installment i2 = Installment.builder().credit(credit).monthNumber(2)
                .paymentAmount(new BigDecimal("505.00")).principalPart(new BigDecimal("500.00"))
                .interestPart(new BigDecimal("5.00")).remainingBalance(new BigDecimal("0.00"))
                .paid(false).build();
        i2.setId(102L);

        credit.getInstallments().add(i1);
        credit.getInstallments().add(i2);
        return credit;
    }
}
