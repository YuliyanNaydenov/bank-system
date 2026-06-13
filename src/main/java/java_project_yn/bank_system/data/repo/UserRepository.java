package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByAuthorities_Authority(String authority);
}
