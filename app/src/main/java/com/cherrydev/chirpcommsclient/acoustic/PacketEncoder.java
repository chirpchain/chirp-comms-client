package com.cherrydev.chirpcommsclient.acoustic;

/**
 * Created by jlunder on 6/29/15.
 */
public class PacketEncoder {
    public static final int MAX_PACKET_SYMBOLS = 400;
    public static final int FRAME_PREAMBLE_SYMBOLS = 4;
    public static final int INTERPACKET_GAP_SYMBOLS = 2;
    public static final int FRAME_HEADER_BYTES = 2;
    public static final int FRAME_FOOTER_BYTES = 4;
    public static final int ECC_EXPANSION_NUM = 2;
    public static final int ECC_EXPANSION_DENOM = 1;
    public static final int MAX_PAYLOAD_BYTES =
            (MAX_PACKET_SYMBOLS - FRAME_PREAMBLE_SYMBOLS - INTERPACKET_GAP_SYMBOLS) *
                    ECC_EXPANSION_DENOM / ECC_EXPANSION_NUM -
                    FRAME_HEADER_BYTES - FRAME_FOOTER_BYTES;

    public static final int PREAMBLE_SYMBOL = 0;
    public static final int START_OF_FRAME_SYMBOL = 1;
    public static final int INTERPACKET_GAP_SYMBOL = -1;

    public int[] encodePacket(Packet packet) {
        int packetBytes = (FRAME_HEADER_BYTES + packet.getPayload().length + FRAME_FOOTER_BYTES);
        int symbolCount = ((FRAME_PREAMBLE_SYMBOLS + INTERPACKET_GAP_SYMBOLS +
                packetBytes) * ECC_EXPANSION_NUM + ECC_EXPANSION_DENOM - 1) / ECC_EXPANSION_DENOM;
        int[] symbols = new int[symbolCount];
        int i = 0;

        for(int j = 0; j < FRAME_PREAMBLE_SYMBOLS - 1; ++j) {
            symbols[i++] = PREAMBLE_SYMBOL;
        }
        symbols[i++] = START_OF_FRAME_SYMBOL;
        for(int j = 0; j < INTERPACKET_GAP_SYMBOLS; ++j) {
            symbols[i++] = INTERPACKET_GAP_SYMBOL;
        }

        return symbols;
    }
}
