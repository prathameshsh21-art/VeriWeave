package com.veriweave.auth.repository;

import com.veriweave.auth.entity.AuthVerificationToken;
import com.veriweave.auth.entity.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthVerificationTokenRepository extends JpaRepository<AuthVerificationToken, Long> {

    List<AuthVerificationToken> findAllByEmailAndTokenTypeOrderByCreatedAtDesc(String email, TokenType tokenType);

    Optional<AuthVerificationToken> findFirstByEmailAndTokenTypeAndUsedAtIsNullOrderByCreatedAtDesc(String email, TokenType tokenType);
}
