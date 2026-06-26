package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.TransactionDTO;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {
    TransactionDTO deposit(long accountId, BigDecimal amount);
    TransactionDTO withdraw(long accountId, BigDecimal amount);
    void transfer(long fromAccountId, String toIban, BigDecimal amount);
    List<TransactionDTO> getByAccount(long accountId);
    List<TransactionDTO> getByUsername(String username);
}
