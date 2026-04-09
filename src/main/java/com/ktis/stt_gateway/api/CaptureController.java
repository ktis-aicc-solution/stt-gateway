package com.ktis.stt_gateway.api;

import com.ktis.stt_gateway.capture.PacketCaptureService;
import com.ktis.stt_gateway.session.CallSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/capture")
@RequiredArgsConstructor
@Slf4j
public class CaptureController {

    private final PacketCaptureService captureService;
    private final CallSessionManager sessionManager;

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startCapture() {
        captureService.startCapture();
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stopCapture() {
        captureService.stopCapture();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCaptureStatus() {
        return ResponseEntity.ok(Map.of(
            "running", captureService.isRunning(),
            "activeSessionCount", sessionManager.getActiveSessionCount()
        ));
    }

    @PostMapping("/pcap")
    public ResponseEntity<Map<String, String>> readPcapFile(@RequestParam String filePath) {
        captureService.readPcapFile(filePath);
        return ResponseEntity.ok(Map.of("status", "processing", "file", filePath));
    }
}
