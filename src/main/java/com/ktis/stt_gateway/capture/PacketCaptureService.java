package com.ktis.stt_gateway.capture;

import com.ktis.stt_gateway.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class PacketCaptureService {

    private final AppProperties props;
    private final PacketDispatcher dispatcher;

    private PcapHandle handle;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void startCapture() {
        if (running.get()) {
            log.warn("패킷 캡처가 이미 실행 중입니다.");
            return;
        }

        String interfaceName = props.getCapture().getInterfaceName();
        try {
            PcapNetworkInterface nif = Pcaps.getDevByName(interfaceName);
            if (nif == null) {
                log.error("네트워크 인터페이스를 찾을 수 없습니다: {}", interfaceName);
                return;
            }

            handle = nif.openLive(65535, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 100);
            handle.setFilter(props.getCapture().getFilter(), BpfProgram.BpfCompileMode.OPTIMIZE);

            running.set(true);
            Thread captureThread = new Thread(this::captureLoop, "packet-capture");
            captureThread.setDaemon(true);
            captureThread.start();

            log.info("패킷 캡처 시작: interface={}, filter={}", interfaceName, props.getCapture().getFilter());
        } catch (Exception e) {
            log.error("패킷 캡처 시작 실패: {}", e.getMessage(), e);
        }
    }

    public void stopCapture() {
        running.set(false);
        if (handle != null && handle.isOpen()) {
            handle.close();
        }
        log.info("패킷 캡처 중지");
    }

    public boolean isRunning() {
        return running.get();
    }

    public void readPcapFile(String filePath) {
        try (PcapHandle offlineHandle = Pcaps.openOffline(filePath)) {
            log.info("pcap 파일 읽기 시작: {}", filePath);
            offlineHandle.loop(-1, (RawPacketListener) rawData ->
                dispatcher.dispatch(rawData, new Timestamp(Instant.now().toEpochMilli()))
            );
            log.info("pcap 파일 읽기 완료: {}", filePath);
        } catch (Exception e) {
            log.error("pcap 파일 읽기 실패: path={}, error={}", filePath, e.getMessage(), e);
        }
    }

    private void captureLoop() {
        try {
            handle.loop(-1, (RawPacketListener) rawData -> {
                if (!running.get()) return;
                Timestamp timestamp = new Timestamp(handle.getTimestamp().getTime());
                dispatcher.dispatch(rawData, timestamp);
            });
        } catch (PcapNativeException | InterruptedException e) {
            if (running.get()) {
                log.error("패킷 캡처 루프 오류: {}", e.getMessage(), e);
            }
        } catch (NotOpenException e) {
            log.debug("pcap 핸들 닫힘 (정상 종료)");
        } finally {
            running.set(false);
        }
    }
}
