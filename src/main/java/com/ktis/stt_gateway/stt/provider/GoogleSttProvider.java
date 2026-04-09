package com.ktis.stt_gateway.stt.provider;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttMode;
import com.ktis.stt_gateway.exception.SttProcessingException;
import com.ktis.stt_gateway.stt.SttProvider;
import com.ktis.stt_gateway.stt.SttRequest;
import com.ktis.stt_gateway.stt.SttResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Component("googleSttProvider")
@Slf4j
public class GoogleSttProvider implements SttProvider {

    @Value("${stt.gateway.google.credentials-file-path:#{null}}")
    private String credentialsFilePath;

    @Override
    public String getProviderName() {
        return "google";
    }

    @Override
    public SttResult transcribeBatch(SttRequest request) {
        try (SpeechClient speechClient = createSpeechClient()) {
            byte[] audioData = loadAudioData(request);
            if (audioData == null || audioData.length == 0) {
                return buildErrorResult(request, "오디오 데이터가 없습니다.");
            }

            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(request.getSampleRate())
                .setLanguageCode(request.getLanguageCode() != null ? request.getLanguageCode() : "ko-KR")
                .setEnableAutomaticPunctuation(true)
                .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .build();

            RecognizeResponse response = speechClient.recognize(config, audio);

            StringBuilder transcript = new StringBuilder();
            float totalConfidence = 0;
            int resultCount = 0;

            for (SpeechRecognitionResult result : response.getResultsList()) {
                if (result.getAlternativesCount() > 0) {
                    SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                    transcript.append(alternative.getTranscript());
                    totalConfidence += alternative.getConfidence();
                    resultCount++;
                }
            }

            return SttResult.builder()
                .callId(request.getCallId())
                .channel(request.getChannel())
                .transcript(transcript.toString())
                .confidence(resultCount > 0 ? totalConfidence / resultCount : null)
                .sttMode(SttMode.BATCH)
                .isFinal(true)
                .build();

        } catch (Exception e) {
            log.error("Google STT 배치 처리 실패: callId={}, channel={}",
                request.getCallId(), request.getChannel(), e);
            return buildErrorResult(request, e.getMessage());
        }
    }

    @Override
    public void transcribeStreaming(SttRequest request, Consumer<SttResult> consumer) {
        throw new UnsupportedOperationException("스트리밍 STT는 별도 스트리밍 서비스에서 처리합니다.");
    }

    @Override
    public void stopStreaming(String sessionKey) {
        // 스트리밍 중지 로직은 스트리밍 서비스에서 관리
    }

    @Override
    public boolean isAvailable() {
        return credentialsFilePath != null && Files.exists(Path.of(credentialsFilePath));
    }

    private SpeechClient createSpeechClient() throws IOException {
        return SpeechClient.create();
    }

    private byte[] loadAudioData(SttRequest request) throws IOException {
        if (request.getAudioData() != null) return request.getAudioData();
        if (request.getAudioFilePath() != null) {
            return Files.readAllBytes(Path.of(request.getAudioFilePath()));
        }
        return null;
    }

    private SttResult buildErrorResult(SttRequest request, String errorMessage) {
        return SttResult.builder()
            .callId(request.getCallId())
            .channel(request.getChannel() != null ? request.getChannel() : AudioChannel.RX)
            .sttMode(SttMode.BATCH)
            .isFinal(true)
            .errorMessage(errorMessage)
            .build();
    }
}
