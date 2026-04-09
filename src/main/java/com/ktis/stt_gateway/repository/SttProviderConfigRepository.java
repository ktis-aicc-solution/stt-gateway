package com.ktis.stt_gateway.repository;

import com.ktis.stt_gateway.domain.SttProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SttProviderConfigRepository extends JpaRepository<SttProviderConfig, Long> {

    Optional<SttProviderConfig> findByIsActiveTrue();

    Optional<SttProviderConfig> findByProviderName(String providerName);

    @Modifying
    @Query("UPDATE SttProviderConfig s SET s.isActive = false WHERE s.isActive = true")
    void deactivateAll();
}
