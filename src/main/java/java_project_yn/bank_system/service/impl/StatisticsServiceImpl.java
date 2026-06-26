package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.CreditStatus;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.dto.ChartPointDTO;
import java_project_yn.bank_system.dto.StatisticsDTO;
import java_project_yn.bank_system.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CreditRepository creditRepository;

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public StatisticsDTO getOverview() {
        List<ChartPointDTO> creditsByType = creditRepository.countGroupedByCreditType().stream()
                .map(row -> ChartPointDTO.builder()
                        .label((String) row[0])
                        .value(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return StatisticsDTO.builder()
                .totalClients(clientRepository.count())
                .totalAccounts(accountRepository.count())
                .activeAccounts(accountRepository.countByStatus(AccountStatus.ACTIVE))
                .activeCredits(creditRepository.countByStatus(CreditStatus.ACTIVE))
                .paidCredits(creditRepository.countByStatus(CreditStatus.PAID))
                .totalBalance(accountRepository.sumAllBalances())
                .totalGranted(creditRepository.sumAllAmounts())
                .creditsByType(creditsByType)
                .build();
    }
}
