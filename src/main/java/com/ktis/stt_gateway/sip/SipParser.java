package com.ktis.stt_gateway.sip;

import com.ktis.stt_gateway.session.CallSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.packet.IpV4Packet;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class SipParser {

    private static final Pattern PHONE_PATTERN = Pattern.compile("sip:(\\d+)@");
    private static final Pattern RTP_PORT_PATTERN = Pattern.compile("m=audio (\\d+)");

    private final CallSessionManager sessionManager;

    public void parse(byte[] payload, IpV4Packet ipPacket, Timestamp timestamp) {
        try {
            String sipText = new String(payload, StandardCharsets.UTF_8);
            if (!sipText.contains("SIP/2.0")) return;

            SipMessage message = parseSipMessage(sipText, ipPacket);
            if (message.getCallId() == null || message.getCallId().isBlank()) return;

            switch (message.getMethod()) {
                case INVITE  -> sessionManager.onCallStart(message, timestamp);
                case ACK     -> sessionManager.onCallConnected(message, timestamp);
                case BYE     -> sessionManager.onCallEnd(message, timestamp);
                case CANCEL  -> sessionManager.onCallEnd(message, timestamp);
                default      -> log.debug("SIP 메시지 무시: method={}, callId={}", message.getMethod(), message.getCallId());
            }
        } catch (Exception e) {
            log.debug("SIP 파싱 오류 무시: {}", e.getMessage());
        }
    }

    private SipMessage parseSipMessage(String sipText, IpV4Packet ipPacket) {
        String[] lines = sipText.split("\r\n");
        SipMessage.SipMessageBuilder builder = SipMessage.builder();

        SipMethod method = parseMethod(lines[0]);
        builder.method(method);

        for (String line : lines) {
            String lineLower = line.toLowerCase();
            if (lineLower.startsWith("call-id:") || lineLower.startsWith("i:")) {
                builder.callId(extractValue(line).trim());
            } else if (lineLower.startsWith("from:") || lineLower.startsWith("f:")) {
                builder.fromNumber(extractPhoneNumber(line));
            } else if (lineLower.startsWith("to:") || lineLower.startsWith("t:")) {
                builder.toNumber(extractPhoneNumber(line));
            } else if (lineLower.startsWith("m=audio")) {
                int rtpPort = extractRtpPort(line);
                if (ipPacket != null) {
                    builder.customerIp(ipPacket.getHeader().getSrcAddr().getHostAddress());
                    builder.customerRtpPort(rtpPort);
                }
            }
        }

        return builder.build();
    }

    private SipMethod parseMethod(String firstLine) {
        if (firstLine.startsWith("INVITE")) return SipMethod.INVITE;
        if (firstLine.startsWith("ACK"))    return SipMethod.ACK;
        if (firstLine.startsWith("BYE"))    return SipMethod.BYE;
        if (firstLine.startsWith("CANCEL")) return SipMethod.CANCEL;
        return SipMethod.UNKNOWN;
    }

    private String extractValue(String line) {
        int colonIdx = line.indexOf(':');
        return colonIdx >= 0 ? line.substring(colonIdx + 1).trim() : "";
    }

    private String extractPhoneNumber(String line) {
        Matcher matcher = PHONE_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private int extractRtpPort(String line) {
        Matcher matcher = RTP_PORT_PATTERN.matcher(line);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
