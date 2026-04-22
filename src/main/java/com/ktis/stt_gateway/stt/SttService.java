package com.ktis.stt_gateway.stt;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.config.AppProperties;
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

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SttService {

    private final SttProviderFactory providerFactory;
    private final SttResultRepository sttResultRepository;
    private final WebSocketPushService pushService;
    private final AppProperties props;

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

            String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String fileName = session.getCallId() + "_" + channel.name().toLowerCase() + ".wav";
            String audioFilePath = Path.of(props.getAudio().getOutputPath(), dateDir, fileName).toString();

            SttRequest request = SttRequest.builder()
                .callId(session.getCallId())
                .channel(channel)
                .languageCode("ko-KR")
                .sampleRate(8000)
                .audioFilePath(audioFilePath)
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

    /** 스트리밍 STT 최종 결과 DB 저장 (WebSocket push는 SttStreamingManager가 직접 처리) */
    @Transactional
    public void saveResult(SttResult result) {
        SttResultEntity entity = SttResultEntity.builder()
            .callId(result.getCallId())
            .channel(result.getChannel())
            .transcript(result.getTranscript())
            .confidence(result.getConfidence())
            .sttMode(result.getSttMode() != null ? result.getSttMode() : SttMode.STREAMING)
            .isFinal(result.isFinal())
            .errorMessage(result.getErrorMessage())
            .build();
        sttResultRepository.save(entity);
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
