package com.ktis.stt_gateway.audio;

import com.ktis.stt_gateway.config.AppProperties;
import com.ktis.stt_gateway.domain.AudioFile;
import com.ktis.stt_gateway.repository.AudioFileRepository;
import com.ktis.stt_gateway.session.CallSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AudioFileWriter {

    private final AppProperties props;
    private final AudioFileRepository audioFileRepository;

    @Async("fileWriteExecutor")
    public CompletableFuture<Void> finalizeAndSave(CallSession session) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path audioDir = Path.of(props.getAudio().getOutputPath(), dateDir);

        try {
            Files.createDirectories(audioDir);
            saveChannel(session, audioDir, AudioChannel.RX);
            saveChannel(session, audioDir, AudioChannel.TX);
        } catch (Exception e) {
            log.error("오디오 파일 저장 실패: callId={}", session.getCallId(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void saveChannel(CallSession session, Path audioDir, AudioChannel channel) throws IOException {
        Queue<short[]> buffer = channel == AudioChannel.RX
            ? session.getRxBuffer()
            : session.getTxBuffer();

        byte[] pcmData = drainBuffer(buffer);
        if (pcmData.length == 0) return;

        String fileName = session.getCallId() + "_" + channel.name().toLowerCase() + ".wav";
        Path filePath = audioDir.resolve(fileName);

        byte[] wavHeader = WavFileBuilder.createHeader(
            pcmData.length,
            props.getAudio().getSampleRate(),
            1,
            props.getAudio().getBitsPerSample()
        );

        try (BufferedOutputStream bos = new BufferedOutputStream(
                Files.newOutputStream(filePath), 65536)) {
            bos.write(wavHeader);
            bos.write(pcmData);
        }

        long fileSize = Files.size(filePath);
        audioFileRepository.save(AudioFile.builder()
            .callId(session.getCallId())
            .channel(channel)
            .filePath(filePath.toString())
            .fileName(fileName)
            .fileSize(fileSize)
            .format("WAV")
            .sampleRate(props.getAudio().getSampleRate())
            .build());

        log.info("오디오 파일 저장 완료: callId={}, channel={}, path={}",
            session.getCallId(), channel, filePath);
    }

    private byte[] drainBuffer(Queue<short[]> buffer) {
        int totalSamples = buffer.stream().mapToInt(a -> a.length).sum();
        ByteBuffer pcmBuffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN);

        short[] chunk;
        while ((chunk = buffer.poll()) != null) {
            for (short sample : chunk) {
                pcmBuffer.putShort(sample);
            }
        }
        return pcmBuffer.array();
    }
}
