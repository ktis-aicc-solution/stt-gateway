package com.ktis.stt_gateway.dto;

import com.ktis.stt_gateway.domain.SttProviderConfig;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SttProviderConfigDto {
    private Long id;
    private String providerName;
    private String displayName;
    private Boolean isActive;
    private String languageCode;
    private Boolean supportsStreaming;
    private Boolean supportsBatch;
    private String description;

    public static SttProviderConfigDto fromEntity(SttProviderConfig config) {
        return SttProviderConfigDto.builder()
            .id(config.getId())
            .providerName(config.getProviderName())
            .displayName(config.getDisplayName())
            .isActive(config.getIsActive())
            .languageCode(config.getLanguageCode())
            .supportsStreaming(config.getSupportsStreaming())
            .supportsBatch(config.getSupportsBatch())
            .description(config.getDescription())
            .build();
    }
}
