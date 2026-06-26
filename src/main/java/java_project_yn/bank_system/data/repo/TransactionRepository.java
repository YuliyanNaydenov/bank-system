package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccount_IdOrderByTimestampDescIdDesc(long accountId);
    List<Transaction> findByAccount_Owner_UsernameOrderByTimestampDescIdDesc(String username);
}
