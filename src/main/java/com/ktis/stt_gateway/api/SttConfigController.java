package com.ktis.stt_gateway.api;

import com.ktis.stt_gateway.domain.SttProviderConfig;
import com.ktis.stt_gateway.dto.SttProviderConfigDto;
import com.ktis.stt_gateway.exception.SttGatewayException;
import com.ktis.stt_gateway.repository.SttProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/stt/providers")
@RequiredArgsConstructor
@Slf4j
public class SttConfigController {

    private final SttProviderConfigRepository configRepository;

    @GetMapping
    public ResponseEntity<List<SttProviderConfigDto>> getAllProviders() {
        List<SttProviderConfigDto> providers = configRepository.findAll().stream()
            .map(SttProviderConfigDto::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(providers);
    }

    @GetMapping("/active")
    public ResponseEntity<SttProviderConfigDto> getActiveProvider() {
        SttProviderConfig active = configRepository.findByIsActiveTrue()
            .orElseThrow(() -> new SttGatewayException("활성화된 STT 공급자가 없습니다."));
        return ResponseEntity.ok(SttProviderConfigDto.fromEntity(active));
    }

    @PutMapping("/{providerName}/activate")
    @Transactional
    public ResponseEntity<Void> activateProvider(@PathVariable String providerName) {
        SttProviderConfig config = configRepository.findByProviderName(providerName)
            .orElseThrow(() -> new SttGatewayException("STT 공급자를 찾을 수 없습니다: " + providerName));

        configRepository.deactivateAll();
        config.activate();

        log.info("STT 공급자 활성화: {}", providerName);
        return ResponseEntity.noContent().build();
    }
}
