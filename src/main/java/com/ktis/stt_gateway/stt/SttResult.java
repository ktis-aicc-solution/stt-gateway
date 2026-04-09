package com.ktis.stt_gateway.stt;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SttResult {
    private final String callId;
    private final AudioChannel channel;
    private final String transcript;
    private final Float confidence;
    private final Long startOffsetMs;
    private final Long endOffsetMs;
    private final SttMode sttMode;
    private final boolean isFinal;
    private final String errorMessage;

    public boolean isSuccess() {
        return errorMessage == null && transcript != null && !transcript.isBlank();
    }
}
