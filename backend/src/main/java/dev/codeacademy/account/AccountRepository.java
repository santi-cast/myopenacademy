package dev.codeacademy.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByUsernameCanonical(String usernameCanonical);

	boolean existsByUsernameCanonical(String usernameCanonical);
}
