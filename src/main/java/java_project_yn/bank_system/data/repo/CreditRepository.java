package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Credit;
import java_project_yn.bank_system.data.entity.CreditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CreditRepository extends JpaRepository<Credit, Long> {
    List<Credit> findByClient_Id(long clientId);
    List<Credit> findByClient_Username(String username);

    long countByStatus(CreditStatus status);

    boolean existsByCreditType_Id(long creditTypeId);
    boolean existsByClient_IdAndStatus(long clientId, CreditStatus status);

    @Query("select coalesce(sum(c.amount), 0) from Credit c")
    BigDecimal sumAllAmounts();

    @Query("select c.creditType.name, count(c) from Credit c group by c.creditType.name order by c.creditType.name")
    List<Object[]> countGroupedByCreditType();
}
