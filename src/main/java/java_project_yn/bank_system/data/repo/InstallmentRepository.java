package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByCredit_IdOrderByMonthNumberAsc(long creditId);
}
