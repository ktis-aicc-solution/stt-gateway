package com.ktis.stt_gateway.stt;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.config.AppProperties;
import com.ktis.stt_gateway.push.WebSocketPushService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 콜별 실시간 STT 스트리밍 세션을 관리합니다.
 * RTP 디코더에서 PCM 샘플을 받아 Google STT 스트리밍 API로 즉시 전달합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SttStreamingManager {

    private final AppProperties props;
    private final WebSocketPushService pushService;
    private final SttService sttService;

    private SpeechClient speechClient;
    private final ConcurrentHashMap<String, SttStreamSession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            speechClient = createSpeechClient();
            log.info("STT 스트리밍 서비스 초기화 완료");
        } catch (Exception e) {
            log.warn("SpeechClient 초기화 실패 — 스트리밍 STT 비활성화: {}", e.getMessage());
            speechClient = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(SttStreamSession::stop);
        sessions.clear();
        if (speechClient != null) {
            try { speechClient.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * RTP 디코더에서 PCM 샘플 도착 시 호출.
     * 해당 콜/채널의 스트리밍 세션이 없으면 자동 생성.
     */
    public void onAudio(String callId, AudioChannel channel, short[] samples) {
        if (speechClient == null) return;

        String key = key(callId, channel);
        SttStreamSession session = sessions.computeIfAbsent(key, k ->
            new SttStreamSession(
                callId, channel,
                speechClient.streamingRecognizeCallable(),
                this::handleResult,
                props.getStreaming().getMaxDurationSeconds() * 1000
            )
        );
        session.sendAudio(samples);
    }

    /** 콜 종료 시 해당 콜의 모든 채널 스트리밍 세션 종료 */
    public void stopSession(String callId) {
        for (AudioChannel ch : AudioChannel.values()) {
            SttStreamSession session = sessions.remove(key(callId, ch));
            if (session != null) session.stop();
        }
    }

    private void handleResult(SttResult result) {
        // 중간 결과(interim) 포함 즉시 WebSocket push
        pushService.pushSttResult(result);
        // 최종 결과만 DB 저장
        if (result.isFinal()) {
            sttService.saveResult(result);
        }
    }

    private String key(String callId, AudioChannel channel) {
        return callId + "_" + channel.name();
    }

    private SpeechClient createSpeechClient() throws IOException {
        String credPath = props.getGoogle().getCredentialsFile();
        if (credPath != null && Files.exists(Path.of(credPath))) {
            GoogleCredentials creds = GoogleCredentials
                .fromStream(Files.newInputStream(Path.of(credPath)))
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
            SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                .build();
            return SpeechClient.create(settings);
        }
        return SpeechClient.create();
    }
}
