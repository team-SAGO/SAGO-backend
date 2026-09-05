package com.sago.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAuthRepository extends JpaRepository<SocialAuth, Long> {

    Optional<SocialAuth> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
