package com.ktis.stt_gateway.dto;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttMode;
import com.ktis.stt_gateway.domain.SttResultEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SttResultDto {
    private Long id;
    private String callId;
    private AudioChannel channel;
    private String transcript;
    private Float confidence;
    private Long startOffsetMs;
    private Long endOffsetMs;
    private SttMode sttMode;
    private Boolean isFinal;
    private LocalDateTime createdAt;

    public static SttResultDto fromEntity(SttResultEntity entity) {
        return SttResultDto.builder()
            .id(entity.getId())
            .callId(entity.getCallId())
            .channel(entity.getChannel())
            .transcript(entity.getTranscript())
            .confidence(entity.getConfidence())
            .startOffsetMs(entity.getStartOffsetMs())
            .endOffsetMs(entity.getEndOffsetMs())
            .sttMode(entity.getSttMode())
            .isFinal(entity.getIsFinal())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
