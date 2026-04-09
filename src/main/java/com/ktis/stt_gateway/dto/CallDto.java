package com.ktis.stt_gateway.dto;

import com.ktis.stt_gateway.domain.Call;
import com.ktis.stt_gateway.domain.CallStatus;
import com.ktis.stt_gateway.domain.SttMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CallDto {
    private Long id;
    private String callId;
    private String callerNumber;
    private String calleeNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private CallStatus status;
    private String sttProvider;
    private SttMode sttMode;
    private LocalDateTime createdAt;

    public static CallDto fromEntity(Call call) {
        return CallDto.builder()
            .id(call.getId())
            .callId(call.getCallId())
            .callerNumber(maskPhoneNumber(call.getCallerNumber()))
            .calleeNumber(maskPhoneNumber(call.getCalleeNumber()))
            .startTime(call.getStartTime())
            .endTime(call.getEndTime())
            .durationSeconds(call.getDurationSeconds())
            .status(call.getStatus())
            .sttProvider(call.getSttProvider())
            .sttMode(call.getSttMode())
            .createdAt(call.getCreatedAt())
            .build();
    }

    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) return "****";
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(8);
    }
}
