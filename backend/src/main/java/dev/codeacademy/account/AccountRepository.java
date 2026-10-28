package dev.codeacademy.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByUsernameCanonical(String usernameCanonical);

	boolean existsByUsernameCanonical(String usernameCanonical);

	@Query("select a.credentialsVersion from Account a where a.id = :id")
	Optional<Long> findCredentialsVersionById(UUID id);
}
