package com.ktis.stt_gateway.stt;

import com.ktis.stt_gateway.audio.AudioChannel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SttRequest {
    private final String callId;
    private final AudioChannel channel;
    private final String audioFilePath;
    private final byte[] audioData;
    private final String languageCode;
    private final int sampleRate;
}
