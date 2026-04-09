package com.ktis.stt_gateway.stt;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttMode;
import com.ktis.stt_gateway.domain.SttResultEntity;
import com.ktis.stt_gateway.push.WebSocketPushService;
import com.ktis.stt_gateway.repository.SttResultRepository;
import com.ktis.stt_gateway.session.CallSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SttService {

    private final SttProviderFactory providerFactory;
    private final SttResultRepository sttResultRepository;
    private final WebSocketPushService pushService;

    @Async("sttExecutor")
    @Transactional
    public void transcribeBatch(CallSession session) {
        log.info("배치 STT 시작: callId={}", session.getCallId());
        processChannel(session, AudioChannel.RX);
        processChannel(session, AudioChannel.TX);
    }

    private void processChannel(CallSession session, AudioChannel channel) {
        try {
            SttProvider provider = providerFactory.getActiveProvider();

            SttRequest request = SttRequest.builder()
                .callId(session.getCallId())
                .channel(channel)
                .languageCode("ko-KR")
                .sampleRate(8000)
                .build();

            SttResult result = provider.transcribeBatch(request);
            saveAndPush(result, SttMode.BATCH);

            log.info("STT 배치 완료: callId={}, channel={}, length={}",
                session.getCallId(), channel,
                result.getTranscript() != null ? result.getTranscript().length() : 0);
        } catch (Exception e) {
            log.error("STT 배치 처리 실패: callId={}, channel={}", session.getCallId(), channel, e);
            saveSttError(session.getCallId(), channel, e.getMessage(), SttMode.BATCH);
        }
    }

    @Transactional
    public void saveAndPush(SttResult result, SttMode mode) {
        SttResultEntity entity = SttResultEntity.builder()
            .callId(result.getCallId())
            .channel(result.getChannel())
            .transcript(result.getTranscript())
            .confidence(result.getConfidence())
            .startOffsetMs(result.getStartOffsetMs())
            .endOffsetMs(result.getEndOffsetMs())
            .sttMode(mode)
            .isFinal(result.isFinal())
            .errorMessage(result.getErrorMessage())
            .build();

        sttResultRepository.save(entity);
        pushService.pushSttResult(result);
    }

    @Transactional
    public void saveSttError(String callId, AudioChannel channel, String errorMessage, SttMode mode) {
        SttResultEntity entity = SttResultEntity.builder()
            .callId(callId)
            .channel(channel)
            .sttMode(mode)
            .isFinal(true)
            .errorMessage(errorMessage)
            .build();
        sttResultRepository.save(entity);
    }
}
