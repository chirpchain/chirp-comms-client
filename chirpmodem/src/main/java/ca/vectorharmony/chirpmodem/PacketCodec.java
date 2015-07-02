package ca.vectorharmony.chirpmodem;

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
    public static final int ECC_DATA_BLOCK_BYTES = 2;
    public static final int ECC_CODE_BLOCK_SYMBOLS = 16;
    public static final int MAX_PAYLOAD_BYTES =
            ((MAX_PACKET_SYMBOLS - FRAME_PREAMBLE_SYMBOLS - INTERPACKET_GAP_SYMBOLS) /
                    ECC_CODE_BLOCK_SYMBOLS) * ECC_DATA_BLOCK_BYTES - FRAME_HEADER_BYTES -
                    FRAME_FOOTER_BYTES;

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

    private static int[] hammingCodes;
    private static int[] hammingCodeErrorPosition;
    private static int[] hammingCodeCorrectedBits;

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
    private int[] receivedEccSymbols = new int[ECC_CODE_BLOCK_SYMBOLS];

    private int expectedPacketSize = -1;
    private int receivedPacketCurrentSize = 0;
    private byte[] receivedPacket = new byte[MAX_PAYLOAD_BYTES];

    private byte[] validatedReceivedPacket = null;

    public PacketCodec(Modulator modulator, Demodulator demodulator) {
        if (hammingCodes == null) {
            int[] newHammingCodes = new int[16];
            for(int i = 0; i < newHammingCodes.length; ++i) {
                newHammingCodes[i] = makeHammingCode(i);
            }
            int[] newHammingCodeErrorPosition = new int[256];
            int[] newHammingCodeCorrectedBits = new int[256];
            for(int i = 0; i < newHammingCodeErrorPosition.length; ++i) {
                newHammingCodeErrorPosition[i] = makeHammingCodeErrorPosition(i);
                newHammingCodeCorrectedBits[i] = makeHammingCodeCorrectedBits(i);
            }
            synchronized (getClass()) {
                if (hammingCodes == null) {
                    hammingCodes = newHammingCodes;
                }
                if (hammingCodeErrorPosition == null) {
                    hammingCodeErrorPosition = newHammingCodeErrorPosition;
                }
                if (hammingCodeCorrectedBits == null) {
                    hammingCodeCorrectedBits = newHammingCodeCorrectedBits;
                }
            }
        }
        this.modulator = modulator;
        this.demodulator = demodulator;
    }

    private static int makeHammingCode(int d) {
        int p1 = parity(d, 0, 1, 3);
        int p2 = parity(d, 0, 2, 3);
        int p3 = parity(d, 1, 2, 3);
        int p0 = parity(d) ^ p1 ^ p2 ^ p3;
        return (p0 << 0) | (p1 << 1) | (p2 << 2) | (((d >>> 0) & 1) << 3) | (p3 << 4) |
                (((d >>> 1) & 7) << 5);
    }

    private static int makeHammingCodeErrorPosition(int h) {
        int d = (h >>> 4) & 15;
        int p0 = (h >>> 0) & 1;
        int p1 = (h >>> 1) & 1;
        int p2 = (h >>> 2) & 1;
        int p3 = (h >>> 3) & 1;
        int xp0 = parity(d) ^ p1 ^ p2 ^ p3;
        int xp1 = parity(d, 0, 1, 3);
        int xp2 = parity(d, 0, 2, 3);
        int xp3 = parity(d, 1, 2, 3);
        int errorPosition = (p1 == xp1 ? 1 : 0) + (p2 == xp2 ? 2 : 0) + (p3 == xp3 ? 4 : 0);
        if(errorPosition == 0) {
            if(p0 == xp0) {
                // No error
                return -2;
            }
            else {
                return 0;
            }
        }
        else if(errorPosition > 0 && p0 == xp0) {
            // 2-bit error (unrecoverable!)
            return -1;
        }
        else {
            return errorPosition;
        }
    }

    private static int makeHammingCodeCorrectedBits(int h) {
        int p = makeHammingCodeErrorPosition(h);
        if(p == -1) {
            // Uncorrectable
            return -1;
        }
        else {
            int ch = h;
            if(p >= 0) {
                ch = ch ^ (1 << p);
            }
            return (((ch >>> 3) & 1) << 0) | (((ch >>> 5) & 7) << 1);
        }
    }

    private static int parity(int i) {
        return ((i >>> 0) & 1) ^ ((i >>> 1) & 1) ^ ((i >>> 2) & 1) ^ ((i >>> 3) & 1);
    }

    private static int parity(int i, int pos0, int pos1, int pos2) {
        return ((i >>> pos0) & 1) ^ ((i >>> pos1) & 1) ^ ((i >>> pos2) & 1);
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

    public boolean hasNextValidReceivedPacket() {
        return validatedReceivedPacket != null;
    }

    public byte[] nextValidReceivedPacket() {
        byte[] result = validatedReceivedPacket;
        validatedReceivedPacket = null;
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
        receivedEccSymbolCount = 0;
        expectedPacketSize = -1;
        receivedPacketCurrentSize = 0;
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
        int symbolCount = FRAME_PREAMBLE_SYMBOLS + INTERPACKET_GAP_SYMBOLS +
                ((packetBytes + ECC_DATA_BLOCK_BYTES - 1) / ECC_DATA_BLOCK_BYTES) *
                        ECC_CODE_BLOCK_SYMBOLS;
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

    public void encode(byte[] input, int inputOffset, int[] output,
                              int outputOffset, int inputCount) {
        if (inputCount % ECC_DATA_BLOCK_BYTES != 0) {
            throw new IllegalArgumentException(
                    "inputCount must be a multiple of INPUT_BLOCK_SIZE_BYTES");
        }
        int hamming0 = hammingCodes[(input[inputOffset + 0] >>> 0) & 0x0F];
        int hamming1 = hammingCodes[(input[inputOffset + 0] >>> 4) & 0x0F];
        int hamming2 = hammingCodes[(input[inputOffset + 1] >>> 0) & 0x0F];
        int hamming3 = hammingCodes[(input[inputOffset + 1] >>> 4) & 0x0F];
        for(int i = 0; i < 8; ++i) {
            int repackedBits0 = ((hamming0 & 1) << 0) | ((hamming1 & 1) << 1);
            int repackedBits1 = ((hamming2 & 1) << 0) | ((hamming3 & 1) << 1);

            output[outputOffset + i * 2 + 0] = repackedBits0 + 0 + ((i & 1) * 8);
            output[outputOffset + i * 2 + 1] = repackedBits1 + 4 + ((i & 1) * 8);
        }
    }

    public static boolean decode(int[] input, int[] inputTimes, int inputOffset, int[] output,
                                 int outputOffset, int inputCount) {
        return false;
    }
}
