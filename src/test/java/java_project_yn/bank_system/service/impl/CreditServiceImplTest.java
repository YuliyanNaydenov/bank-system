package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.*;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.data.repo.CreditTypeRepository;
import java_project_yn.bank_system.data.repo.InstallmentRepository;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock private CreditRepository creditRepository;
    @Mock private CreditTypeRepository creditTypeRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private InstallmentRepository installmentRepository;

    @InjectMocks
    private CreditServiceImpl creditService;

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
