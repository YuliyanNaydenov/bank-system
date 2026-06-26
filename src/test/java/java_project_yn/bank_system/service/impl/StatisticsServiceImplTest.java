package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.CreditStatus;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.dto.StatisticsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock private ClientRepository clientRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CreditRepository creditRepository;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    @Test
    void getOverview_aggregatesCorrectly() {
        given(clientRepository.count()).willReturn(5L);
        given(accountRepository.count()).willReturn(3L);
        given(accountRepository.countByStatus(AccountStatus.ACTIVE)).willReturn(2L);
        given(accountRepository.sumAllBalances()).willReturn(new BigDecimal("1000.00"));
        given(creditRepository.countByStatus(CreditStatus.ACTIVE)).willReturn(4L);
        given(creditRepository.countByStatus(CreditStatus.PAID)).willReturn(1L);
        given(creditRepository.sumAllAmounts()).willReturn(new BigDecimal("50000.00"));
        given(creditRepository.countGroupedByCreditType()).willReturn(List.of(
                new Object[]{"Потребителски", 3L},
                new Object[]{"Ипотечен", 2L}
        ));

        StatisticsDTO stats = statisticsService.getOverview();

        assertEquals(5L, stats.getTotalClients());
        assertEquals(3L, stats.getTotalAccounts());
        assertEquals(2L, stats.getActiveAccounts());
        assertEquals(4L, stats.getActiveCredits());
        assertEquals(1L, stats.getPaidCredits());
        assertEquals(0, stats.getTotalBalance().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, stats.getTotalGranted().compareTo(new BigDecimal("50000.00")));
        assertEquals(2, stats.getCreditsByType().size());
        assertEquals("Потребителски", stats.getCreditsByType().get(0).getLabel());
        assertEquals(3L, stats.getCreditsByType().get(0).getValue());
    }
}
