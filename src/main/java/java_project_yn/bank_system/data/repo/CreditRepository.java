package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Credit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditRepository extends JpaRepository<Credit, Long> {
    List<Credit> findByClient_Id(long clientId);
    List<Credit> findByClient_Username(String username);
}
