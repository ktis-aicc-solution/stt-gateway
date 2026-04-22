package com.ktis.stt_gateway.rtp;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.session.CallSession;
import com.ktis.stt_gateway.session.CallSessionManager;
import com.ktis.stt_gateway.stt.SttStreamingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.UdpPacket;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Arrays;

@Component
@Slf4j
@RequiredArgsConstructor
public class RtpDecoder {

    private static final int RTP_HEADER_MIN_SIZE = 12;
    private final CallSessionManager sessionManager;
    private final SttStreamingManager sttStreamingManager;
    private final G711Codec codec = new G711Codec();

    public void decode(byte[] payload, IpV4Packet ipPacket, UdpPacket udpPacket, Timestamp timestamp) {
        try {
            if (payload == null || payload.length < RTP_HEADER_MIN_SIZE) return;

            RtpPacket rtpPacket = parseRtpHeader(payload);
            int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
            String srcIp = ipPacket.getHeader().getSrcAddr().getHostAddress();

            CallSession session = sessionManager.findByRtpPort(dstPort, srcIp);
            if (session == null) return;

            short[] pcmSamples = codec.decode(rtpPacket.getPayload(), rtpPacket.getPayloadType());
            AudioChannel channel = session.isCustomerIp(srcIp) ? AudioChannel.RX : AudioChannel.TX;
            session.appendAudio(channel, pcmSamples);                           // 아카이브용 버퍼
            sttStreamingManager.onAudio(session.getCallId(), channel, pcmSamples); // 실시간 STT
        } catch (Exception e) {
            log.debug("RTP 패킷 처리 오류 무시: {}", e.getMessage());
        }
    }

    private RtpPacket parseRtpHeader(byte[] data) {
        int payloadType = data[1] & 0x7F;
        int seqNumber = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        long ssrc = ((data[8] & 0xFFL) << 24) | ((data[9] & 0xFFL) << 16)
                  | ((data[10] & 0xFFL) << 8) | (data[11] & 0xFFL);
        byte[] rtpPayload = Arrays.copyOfRange(data, RTP_HEADER_MIN_SIZE, data.length);
        return new RtpPacket(payloadType, seqNumber, ssrc, rtpPayload);
    }
}
