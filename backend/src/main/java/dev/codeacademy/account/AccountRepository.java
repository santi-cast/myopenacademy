package dev.codeacademy.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByUsernameCanonical(String usernameCanonical);

	boolean existsByUsernameCanonical(String usernameCanonical);

	@Query("select a.credentialsVersion from Account a where a.id = :id")
	Optional<Long> findCredentialsVersionById(UUID id);

	/** Console reset: new hash and next credentials version in one UPDATE, keeping the ID and all other data. */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("""
			update Account a set a.passwordHash = :passwordHash, a.credentialsVersion = a.credentialsVersion + 1
			where a.usernameCanonical = :usernameCanonical""")
	int replacePassword(String usernameCanonical, String passwordHash);

	/** Console promotion (H9): the role changes with the next credentials version, so open sessions take it again. */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("""
			update Account a set a.role = 'admin', a.credentialsVersion = a.credentialsVersion + 1
			where a.usernameCanonical = :usernameCanonical""")
	int promoteToAdmin(String usernameCanonical);
}
