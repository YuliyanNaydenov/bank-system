package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIban(String iban);
    boolean existsByIban(String iban);
    List<Account> findByOwner_Id(long ownerId);
    List<Account> findByOwner_Username(String username);
}
