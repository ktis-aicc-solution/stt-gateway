package com.ktis.stt_gateway.stt;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttMode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 통화 1건 + 채널(RX/TX) 1개의 Google STT 스트리밍 세션을 관리합니다.
 * RTP 패킷이 들어올 때마다 {@link #sendAudio(short[])}를 호출하면
 * 실시간으로 Google STT에 전송되고 결과가 콜백으로 전달됩니다.
 */
@Slf4j
public class SttStreamSession {

    private final String callId;
    private final AudioChannel channel;
    private final BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable;
    private final Consumer<SttResult> onResult;
    private final int maxDurationMs;

    private volatile ApiStreamObserver<StreamingRecognizeRequest> requestObserver;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile long streamStartMs;

    public SttStreamSession(
            String callId,
            AudioChannel channel,
            BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable,
            Consumer<SttResult> onResult,
            int maxDurationMs) {
        this.callId = callId;
        this.channel = channel;
        this.callable = callable;
        this.onResult = onResult;
        this.maxDurationMs = maxDurationMs;
        openStream();
        log.info("스트리밍 STT 세션 시작: callId={}, channel={}", callId, channel);
    }

    private synchronized void openStream() {
        streamStartMs = System.currentTimeMillis();

        requestObserver = callable.bidiStreamingCall(new ApiStreamObserver<>() {
            @Override
            public void onNext(StreamingRecognizeResponse response) {
                handleResponse(response);
            }

            @Override
            public void onError(Throwable t) {
                if (active.get()) {
                    log.warn("스트리밍 STT 오류 (재시작): callId={}, channel={}, error={}",
                        callId, channel, t.getMessage());
                    openStream();
                }
            }

            @Override
            public void onCompleted() {
                log.debug("스트리밍 STT 스트림 완료: callId={}, channel={}", callId, channel);
            }
        });

        // 첫 번째 메시지: 인식 설정
        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
            .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                .setConfig(RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(8000)
                    .setLanguageCode("ko-KR")
                    .setEnableAutomaticPunctuation(true)
                    .build())
                .setInterimResults(true)
                .build())
            .build());
    }

    public synchronized void sendAudio(short[] samples) {
        if (!active.get()) return;

        // Google 5분 제한 → 4분 30초 시점에 스트림 재시작
        if (System.currentTimeMillis() - streamStartMs > maxDurationMs) {
            log.info("스트리밍 5분 제한 도달, 재시작: callId={}, channel={}", callId, channel);
            try { requestObserver.onCompleted(); } catch (Exception ignored) {}
            openStream();
        }

        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
            .setAudioContent(ByteString.copyFrom(toPcmBytes(samples)))
            .build());
    }

    public void stop() {
        if (!active.compareAndSet(true, false)) return;
        synchronized (this) {
            try { requestObserver.onCompleted(); } catch (Exception ignored) {}
        }
        log.info("스트리밍 STT 세션 종료: callId={}, channel={}", callId, channel);
    }

    private void handleResponse(StreamingRecognizeResponse response) {
        for (StreamingRecognitionResult result : response.getResultsList()) {
            if (result.getAlternativesCount() == 0) continue;
            SpeechRecognitionAlternative alt = result.getAlternatives(0);
            try {
                SttResult sttResult = SttResult.builder()
                    .callId(callId)
                    .channel(channel)
                    .transcript(alt.getTranscript())
                    .confidence(result.getIsFinal() ? alt.getConfidence() : null)
                    .isFinal(result.getIsFinal())
                    .sttMode(SttMode.STREAMING)
                    .build();
                onResult.accept(sttResult);
            } catch (Exception e) {
                log.error("STT 결과 처리 오류: callId={}, channel={}", callId, channel, e);
            }
        }
    }

    private static byte[] toPcmBytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2]     = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
