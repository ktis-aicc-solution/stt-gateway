package com.ktis.stt_gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stt.gateway")
@Getter
@Setter
public class AppProperties {

    private Capture capture = new Capture();
    private Audio audio = new Audio();
    private Sip sip = new Sip();
    private Rtp rtp = new Rtp();
    private Streaming streaming = new Streaming();

    @Getter
    @Setter
    public static class Capture {
        private String interfaceName = "eth3";
        private String filter = "udp port 5060 or (udp portrange 10000-20000)";
        private String dumpPath = "/APP_DATA/DUMP";
        private boolean dumpEnabled = true;
        private int dumpRetentionDays = 3;
    }

    @Getter
    @Setter
    public static class Audio {
        private String outputPath = "/APP_DATA/audio";
        private String format = "WAV";
        private int sampleRate = 8000;
        private int bitsPerSample = 16;
        private int retentionDays = 30;
    }

    @Getter
    @Setter
    public static class Sip {
        private int port = 5060;
    }

    @Getter
    @Setter
    public static class Rtp {
        private int portMin = 10000;
        private int portMax = 20000;
    }

    @Getter
    @Setter
    public static class Streaming {
        private int maxDurationSeconds = 270;
        private int overlapSeconds = 2;
    }
}
