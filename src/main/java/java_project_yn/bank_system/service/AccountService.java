package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;

import java.util.List;

public interface AccountService {
    List<AccountDTO> getAllAccounts();
    AccountDTO getAccountById(long id);
    List<AccountDTO> getAccountsByClient(long clientId);
    List<AccountDTO> getAccountsByUsername(String username);
    AccountDTO openAccount(CreateAccountDTO dto);
    AccountDTO closeAccount(long id);
    void deleteAccount(long id);
}
