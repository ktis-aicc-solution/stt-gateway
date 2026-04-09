package com.ktis.stt_gateway.push;

import com.ktis.stt_gateway.stt.SttResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketPushService {

    private static final String STT_TOPIC_PREFIX = "/topic/stt/";
    private final SimpMessagingTemplate messagingTemplate;

    public void pushSttResult(SttResult result) {
        if (result == null || result.getCallId() == null) return;

        try {
            TranscriptionMessage message = TranscriptionMessage.from(result);
            String destination = STT_TOPIC_PREFIX + result.getCallId();
            messagingTemplate.convertAndSend(destination, message);
            log.debug("STT 결과 WebSocket push: callId={}, channel={}, final={}",
                result.getCallId(), result.getChannel(), result.isFinal());
        } catch (Exception e) {
            log.error("WebSocket push 실패: callId={}", result.getCallId(), e);
        }
    }
}
