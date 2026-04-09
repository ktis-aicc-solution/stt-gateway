package com.ktis.stt_gateway.capture;

import com.ktis.stt_gateway.config.AppProperties;
import com.ktis.stt_gateway.rtp.RtpDecoder;
import com.ktis.stt_gateway.sip.SipParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.UdpPacket;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
@Slf4j
@RequiredArgsConstructor
public class PacketDispatcher {

    private final SipParser sipParser;
    private final RtpDecoder rtpDecoder;
    private final AppProperties props;

    public void dispatch(byte[] rawData, Timestamp timestamp) {
        try {
            EthernetPacket ethPacket = EthernetPacket.newPacket(rawData, 0, rawData.length);
            IpV4Packet ipPacket = ethPacket.get(IpV4Packet.class);
            UdpPacket udpPacket = ethPacket.get(UdpPacket.class);

            if (ipPacket == null || udpPacket == null) return;
            if (udpPacket.getPayload() == null) return;

            int srcPort = udpPacket.getHeader().getSrcPort().valueAsInt();
            int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
            byte[] payload = udpPacket.getPayload().getRawData();

            int sipPort = props.getSip().getPort();
            int rtpMin = props.getRtp().getPortMin();
            int rtpMax = props.getRtp().getPortMax();

            if (srcPort == sipPort || dstPort == sipPort) {
                sipParser.parse(payload, ipPacket, timestamp);
            } else if (isRtpPort(srcPort, rtpMin, rtpMax) || isRtpPort(dstPort, rtpMin, rtpMax)) {
                rtpDecoder.decode(payload, ipPacket, udpPacket, timestamp);
            }
        } catch (Exception e) {
            log.debug("패킷 파싱 오류 무시: {}", e.getMessage());
        }
    }

    private boolean isRtpPort(int port, int min, int max) {
        return port >= min && port <= max;
    }
}
