package com.ktis.stt_gateway.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stt_provider_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SttProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false, unique = true, length = 50)
    private String providerName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "language_code", nullable = false, length = 20)
    @Builder.Default
    private String languageCode = "ko-KR";

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "supports_streaming", nullable = false)
    @Builder.Default
    private Boolean supportsStreaming = false;

    @Column(name = "supports_batch", nullable = false)
    @Builder.Default
    private Boolean supportsBatch = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
    public void updateConfig(String newConfigJson) { this.configJson = newConfigJson; }
    public void updateLanguageCode(String languageCode) { this.languageCode = languageCode; }
}
