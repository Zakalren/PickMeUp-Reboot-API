package dev.zakalren.pickmeup.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByServiceNumber(String serviceNumber);

    boolean existsByServiceNumber(String serviceNumber);
}
