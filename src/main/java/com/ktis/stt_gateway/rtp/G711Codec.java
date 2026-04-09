package com.ktis.stt_gateway.rtp;

public class G711Codec {

    private static final short[] ALAW_TABLE = buildAlawTable();

    public short[] decode(byte[] data, int payloadType) {
        short[] pcm = new short[data.length];
        if (payloadType == 8) {        // G.711 A-law (유럽/아시아)
            for (int i = 0; i < data.length; i++) {
                pcm[i] = ALAW_TABLE[data[i] & 0xFF];
            }
        } else if (payloadType == 0) { // G.711 μ-law (미국)
            for (int i = 0; i < data.length; i++) {
                pcm[i] = decodeMulaw(data[i]);
            }
        }
        return pcm;
    }

    private short decodeMulaw(byte mulaw) {
        int ulaw = (~mulaw) & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int linear = ((mantissa << 3) + 0x84) << exponent;
        linear -= 0x84;
        return (short) (sign != 0 ? -linear : linear);
    }

    private static short[] buildAlawTable() {
        short[] table = new short[256];
        for (int i = 0; i < 256; i++) {
            int alaw = i ^ 0x55;
            int sign = alaw & 0x80;
            int exponent = (alaw & 0x70) >> 4;
            int mantissa = alaw & 0x0F;
            int linear = (mantissa << 4) | 0x08;
            if (exponent > 0) linear = (linear + 0x100) << (exponent - 1);
            table[i] = (short) (sign != 0 ? linear : -linear);
        }
        return table;
    }
}
