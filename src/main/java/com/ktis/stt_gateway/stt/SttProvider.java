package com.ktis.stt_gateway.stt;

import java.util.function.Consumer;

public interface SttProvider {

    String getProviderName();

    SttResult transcribeBatch(SttRequest request);

    void transcribeStreaming(SttRequest request, Consumer<SttResult> consumer);

    void stopStreaming(String sessionKey);

    boolean isAvailable();
}
