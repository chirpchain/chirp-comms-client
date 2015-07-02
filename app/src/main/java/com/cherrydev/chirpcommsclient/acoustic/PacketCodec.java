package com.cherrydev.chirpcommsclient.acoustic;

import java.util.Random;

/**
 * Created by jlunder on 6/29/15.
 */
public class PacketCodec {
    public static final int MAX_PACKET_SYMBOLS = 400;
    public static final int FRAME_PREAMBLE_SYMBOLS = 8;
    public static final int INTERPACKET_GAP_SYMBOLS = 2;
    public static final int FRAME_HEADER_BYTES = 1;
    public static final int FRAME_FOOTER_BYTES = 4;
    public static final int ECC_EXPANSION_NUM = 16;
    public static final int ECC_EXPANSION_DENOM = 8;
    public static final int MAX_PAYLOAD_BYTES =
            (MAX_PACKET_SYMBOLS - FRAME_PREAMBLE_SYMBOLS - INTERPACKET_GAP_SYMBOLS) *
                    ECC_EXPANSION_DENOM / ECC_EXPANSION_NUM -
                    FRAME_HEADER_BYTES - FRAME_FOOTER_BYTES;

    public static final int PREAMBLE_SYMBOL = 0;
    public static final int START_OF_FRAME_SYMBOL = 1;
    public static final int INTERPACKET_GAP_SYMBOL = -1;

    public static final int BACKOFF_MIN = 1;
    public static final int BACKOFF_MAX = 20;

    private static final int DECODER_IDLE = 1;
    private static final int DECODER_START_OF_FRAME = 2;
    private static final int DECODER_PAYLOAD = 3;

    private static final int CONSECUTIVE_START_OF_FRAME_RESET = 3;
    private static final int CONSECUTIVE_BREAK_RESET = 2;

    private Random random = new Random();

    private Modulator modulator;
    private Demodulator demodulator;

    private int preSendDelayRemaining = 0;
    private byte[] queuedPacket = null;

    private int straySymbolCount = 0;
    private int garbledPacketCount = 0;

    private int decoderState = DECODER_IDLE;
    private int consecutiveStartOfFrameCount = 0;
    private int consecutiveBreakCount = 0;
    private int rowsSinceLastReceivedSymbol = 0;

    private int receivedEccSymbolCount = 0;
    private int[] receivedEccSymbols = new int[ECC_EXPANSION_NUM];

    private int expectedPacketSize = -1;
    private int receivedPacketCurrentSize = 0;
    private byte[] receivedPacket = new byte[MAX_PAYLOAD_BYTES];

    public PacketCodec(Modulator modulator, Demodulator demodulator) {
        this.modulator = modulator;
        this.demodulator = demodulator;
    }

    public int getAndResetStraySymbolCount() {
        int result = straySymbolCount;
        straySymbolCount = 0;
        return result;
    }

    public int getAndResetGarbledPacketCount() {
        int result = garbledPacketCount;
        garbledPacketCount = 0;
        return result;
    }

    public boolean isReadyToSend() {
        return !demodulator.isCarrierPresent() && !modulator.isActive();
    }

    public boolean isSending() {
        return modulator.isActive() || preSendDelayRemaining > 0 || queuedPacket != null;
    }

    public boolean isSendQueueFull() {
        return queuedPacket != null;
    }

    public void delaySend(int delaySymbols) {
        if(queuedPacket != null) {
            throw new IllegalStateException("Packet already queued for send, wait for packet send to begin before delaying again");
        }
        preSendDelayRemaining = Math.max(delaySymbols, preSendDelayRemaining);
        updateSendQueue();
    }

    public void sendPacket(byte[] packet) {
        if(isSendQueueFull()) {
            throw new IllegalStateException("Send queue is full");
        }
        queuedPacket = packet;
        updateSendQueue();
    }

    public void update() {
        updateReceive();
        modulator.update();
        updateSendQueue();
    }

    private void updateReceive() {
        while(demodulator.hasNextReceivedSymbol()) {
            int sym = demodulator.nextReceivedSymbol();

            if (sym == START_OF_FRAME_SYMBOL) {
                ++consecutiveStartOfFrameCount;
            } else {
                consecutiveStartOfFrameCount = 0;
            }

            if (sym < 0) {
                ++consecutiveBreakCount;
            } else {
                consecutiveBreakCount = 0;
            }

            if(consecutiveBreakCount >= CONSECUTIVE_BREAK_RESET) {
                cancelPacketReceive();
                decoderState = DECODER_IDLE;
            }
            else if(consecutiveStartOfFrameCount >= CONSECUTIVE_START_OF_FRAME_RESET) {
                if(decoderState != DECODER_IDLE && decoderState != DECODER_START_OF_FRAME) {
                    cancelPacketReceive();
                }
                decoderState = DECODER_START_OF_FRAME;
            }
            else if (decoderState == DECODER_IDLE) {
                if(sym != START_OF_FRAME_SYMBOL) {
                    ++straySymbolCount;
                }
            }
            else {
                receiveSymbolIntoPacket(sym);
            }
        }
    }

    private void cancelPacketReceive() {

    }

    private void receiveSymbolIntoPacket(int symbol) {

    }

    private void updateSendQueue() {
        if(isReadyToSend()) {
            // Air is clear and we're not delaying or transmitting
            if(preSendDelayRemaining > 0) {
                // The air must have just cleared, begin our delay
                modulator.delaySend(preSendDelayRemaining);
                preSendDelayRemaining = 0;
            }
            else {
                // Our delay is done now, transmit
                modulator.sendSymbols(encodePacket(queuedPacket));
                queuedPacket = null;
            }
        }
        else if(!modulator.isActive() && demodulator.isCarrierPresent() &&
                preSendDelayRemaining == 0) {
            // We're not delaying or transmitting ourselves, but someone else just decided to
            // transmit, so let's wait for them to finish and also delay by some backoff amount
            // after they're done transmitting
            preSendDelayRemaining = random.nextInt(BACKOFF_MAX - BACKOFF_MIN + 1) + BACKOFF_MIN;
        }
    }

    private static int[] encodePacket(byte[] payload) {
        int packetBytes = (FRAME_HEADER_BYTES + payload.length + FRAME_FOOTER_BYTES);
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
