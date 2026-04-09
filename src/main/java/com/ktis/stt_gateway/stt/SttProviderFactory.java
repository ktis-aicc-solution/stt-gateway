package com.ktis.stt_gateway.stt;

import com.ktis.stt_gateway.exception.SttProcessingException;
import com.ktis.stt_gateway.repository.SttProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SttProviderFactory {

    private final SttProviderConfigRepository configRepository;
    private final List<SttProvider> providers;

    public SttProvider getActiveProvider() {
        String activeProviderName = configRepository.findByIsActiveTrue()
            .map(config -> config.getProviderName())
            .orElseThrow(() -> new SttProcessingException("활성화된 STT 공급자가 없습니다."));

        Map<String, SttProvider> providerMap = providers.stream()
            .collect(Collectors.toMap(SttProvider::getProviderName, Function.identity()));

        SttProvider provider = providerMap.get(activeProviderName);
        if (provider == null) {
            throw new SttProcessingException("STT 공급자를 찾을 수 없습니다: " + activeProviderName);
        }
        if (!provider.isAvailable()) {
            throw new SttProcessingException("STT 공급자를 사용할 수 없습니다: " + activeProviderName);
        }

        return provider;
    }
}
