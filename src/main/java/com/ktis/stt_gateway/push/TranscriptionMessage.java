package com.ktis.stt_gateway.push;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.stt.SttResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TranscriptionMessage {
    private final String callId;
    private final AudioChannel channel;
    private final String transcript;
    private final Float confidence;
    private final Long startOffsetMs;
    private final Long endOffsetMs;
    private final boolean isFinal;
    private final LocalDateTime timestamp;

    public static TranscriptionMessage from(SttResult result) {
        return TranscriptionMessage.builder()
            .callId(result.getCallId())
            .channel(result.getChannel())
            .transcript(result.getTranscript())
            .confidence(result.getConfidence())
            .startOffsetMs(result.getStartOffsetMs())
            .endOffsetMs(result.getEndOffsetMs())
            .isFinal(result.isFinal())
            .timestamp(LocalDateTime.now())
            .build();
    }
}
