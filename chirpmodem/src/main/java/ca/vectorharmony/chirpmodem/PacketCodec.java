package ca.vectorharmony.chirpmodem;

import java.util.ArrayList;
import java.util.Random;

import ca.vectorharmony.chirpmodem.util.CodeLibrary;

/**
 * Created by jlunder on 6/29/15.
 */
public class PacketCodec {
    public static final int MAX_PACKET_SYMBOLS = 400;
    public static final int FRAME_PREAMBLE_SYMBOLS = 8;
    public static final int INTERPACKET_GAP_SYMBOLS = 2;
    public static final int ECC_DATA_BLOCK_BYTES = 2;
    public static final int ECC_CODE_BLOCK_SYMBOLS = 16;
    public static final int FRAME_HEADER_BYTES = 2;
    public static final int FRAME_FOOTER_BYTES = 4;
    public static final int MAX_PACKET_BYTES =
            (MAX_PACKET_SYMBOLS / ECC_CODE_BLOCK_SYMBOLS) * ECC_DATA_BLOCK_BYTES;
    public static final int MAX_PAYLOAD_BYTES =
            MAX_PACKET_BYTES - FRAME_HEADER_BYTES - FRAME_FOOTER_BYTES;

    public static final int PREAMBLE_SYMBOL = 0;
    public static final int INTERPACKET_GAP_SYMBOL = -1;

    public static final byte FRAME_SIGNATURE = (byte)0xAA;

    public static final int BACKOFF_MIN = 1;
    public static final int BACKOFF_MAX = 20;

    private static final int DECODER_IDLE = 1;
    private static final int DECODER_PREAMBLE = 2;
    private static final int DECODER_PAYLOAD = 3;

    private static final int CONSECUTIVE_PREAMBLE_RESET = 3;
    private static final int CONSECUTIVE_BREAK_RESET = 2;
    private static final int CONSECUTIVE_FALSE_START_RESET = FRAME_PREAMBLE_SYMBOLS;

    private static final int GAP_EPSILON_ROWS = 3;

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
    private int consecutivePreambleCount = 0;
    private int consecutiveBreakCount = 0;
    private int falseStartCount = 0;

    private int receivedEccSymbolCount = 0;
    private int[] receivedEccSymbols = new int[ECC_CODE_BLOCK_SYMBOLS];
    private int[] receivedEccSymbolTimes = new int[ECC_CODE_BLOCK_SYMBOLS];

    private int expectedPacketSize = -1;
    private int expectedPayloadSize = 0;
    private int receivedPacketCurrentSize = 0;
    private byte[] receivedPacket = new byte[MAX_PAYLOAD_BYTES];

    private byte[] validatedReceivedPacket = null;

    public CodeLibrary getLibrary() {
        return Modulator.library;
    }

    public PacketCodec(AudioReceiver receiver, AudioTransmitter transmitter) {
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

                for(int i = 0; i < 16; ++i) {
                    int code = hammingCodes[i];
                    if(hammingCodeErrorPosition[code] != -2) {
                        System.err.println("Hamming code mismatch for " + i);
                    }
                    for(int j = 0; j < 8; ++j) {
                        int mutatedCode = code ^ (1 << j);
                        if(hammingCodeErrorPosition[mutatedCode] != j) {
                            System.err.println("Hamming code failed to detect error at " + j);
                        }
                        if(hammingCodeCorrectedBits[mutatedCode] != i) {
                            System.err.println("Hamming code failed to correct error at " + j);
                        }
                        for(int k = j + 1; k < 8; ++k) {
                            int doubleMutatedCode = mutatedCode ^ (1 << k);
                            if(hammingCodeErrorPosition[doubleMutatedCode] != -1) {
                                System.err.println("Hamming code (error pos) failed to detect double error at " + j + " and " + k);
                            }
                            if(hammingCodeCorrectedBits[doubleMutatedCode] != -1) {
                                System.err.println("Hamming code (corrected bits) failed to detect double error at " + j + " and " + k);
                            }
                        }
                    }
                }
            }
        }

        this.modulator = new Modulator(transmitter);
        this.demodulator = new Demodulator(receiver);
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
        int d = (((h >>> 3) & 1) << 0) | (((h >>> 5) & 7) << 1);
        int p0 = (h >>> 0) & 1;
        int p1 = (h >>> 1) & 1;
        int p2 = (h >>> 2) & 1;
        int p3 = (h >>> 4) & 1;
        int xp0 = parity(d) ^ p1 ^ p2 ^ p3;
        int xp1 = parity(d, 0, 1, 3);
        int xp2 = parity(d, 0, 2, 3);
        int xp3 = parity(d, 1, 2, 3);
        int errorPosition = (p1 == xp1 ? 0 : 1) + (p2 == xp2 ? 0 : 2) + (p3 == xp3 ? 0 : 4);
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
        return 1 ^ ((i >>> 0) & 1) ^ ((i >>> 1) & 1) ^ ((i >>> 2) & 1) ^ ((i >>> 3) & 1);
    }

    private static int parity(int i, int pos0, int pos1, int pos2) {
        return 1 ^ ((i >>> pos0) & 1) ^ ((i >>> pos1) & 1) ^ ((i >>> pos2) & 1);
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

            if (sym == PREAMBLE_SYMBOL) {
                ++consecutivePreambleCount;
            } else {
                consecutivePreambleCount = 0;
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
            else if(consecutivePreambleCount >= CONSECUTIVE_PREAMBLE_RESET) {
                if(decoderState != DECODER_IDLE && decoderState != DECODER_PREAMBLE) {
                    cancelPacketReceive();
                }
                decoderState = DECODER_PREAMBLE;
            }
            else if (decoderState == DECODER_IDLE) {
                if(sym != PREAMBLE_SYMBOL) {
                    ++straySymbolCount;
                }
            }
            else {
                receiveSymbolIntoPacket(sym, demodulator.getLastReceivedSymbolTime());
            }
        }
    }

    private void cancelPacketReceive() {
        receivedEccSymbolCount = 0;
        expectedPacketSize = -1;
        expectedPayloadSize = 0;
        receivedPacketCurrentSize = 0;
        falseStartCount = 0;
    }

    private void receiveSymbolIntoPacket(int symbol, int time) {
        receivedEccSymbols[receivedEccSymbolCount] = symbol;
        receivedEccSymbolTimes[receivedEccSymbolCount] = time;
        ++receivedEccSymbolCount;

        System.err.println("Received symbol: " + symbol);

        if(receivedEccSymbolCount == ECC_CODE_BLOCK_SYMBOLS) {
            int decodedSymbols = decodeOneBlock(receivedEccSymbols, receivedEccSymbolTimes, 0,
                    receivedPacket, receivedPacketCurrentSize);

            if(decoderState == DECODER_PREAMBLE && decodedSymbols >= 0) {
                int foundPayloadSize = (int)receivedPacket[1] & 0xFF;
                if(receivedPacket[0] == FRAME_SIGNATURE && foundPayloadSize <= MAX_PAYLOAD_BYTES) {
                    expectedPayloadSize = foundPayloadSize;
                    expectedPacketSize = ((foundPayloadSize + ECC_DATA_BLOCK_BYTES - 1) /
                            ECC_DATA_BLOCK_BYTES) * ECC_DATA_BLOCK_BYTES + FRAME_HEADER_BYTES +
                            FRAME_FOOTER_BYTES;
                    decoderState = DECODER_PAYLOAD;
                    receivedPacketCurrentSize += ECC_DATA_BLOCK_BYTES;
                    receivedEccSymbolCount = 0;
                }
                else {
                    // false start... what if we skip past that first symbol?
                    ++falseStartCount;
                    if(falseStartCount < CONSECUTIVE_FALSE_START_RESET) {
                        --receivedEccSymbolCount;
                        System.arraycopy(receivedEccSymbols, 1, receivedEccSymbols, 0,
                                receivedEccSymbolCount);
                        System.arraycopy(receivedEccSymbolTimes, 1, receivedEccSymbolTimes, 0,
                                receivedEccSymbolCount);
                    }
                    else {
                        cancelPacketReceive();
                    }
                }
            }
            else if(decoderState == DECODER_PAYLOAD) {
                if(decodedSymbols >= 0) {
                    receivedPacketCurrentSize += ECC_DATA_BLOCK_BYTES;
                    receivedEccSymbolCount = 0;
                    if (tryValidateReceivedPacket()) {
                        cancelPacketReceive();
                    }
                }
                else {
                    cancelPacketReceive();
                }
            }
            else {
                cancelPacketReceive();
            }
        }
    }

    private boolean tryValidateReceivedPacket() {
        if(receivedPacketCurrentSize < expectedPacketSize) {
            return false;
        }
        if(receivedPacketCurrentSize > expectedPacketSize) {
            return true;
        }
        // Bullshit CRC.
        // TODO actually compute a CRC here
        if(receivedPacket[expectedPacketSize - FRAME_FOOTER_BYTES + 0] != 11 ||
                receivedPacket[expectedPacketSize - FRAME_FOOTER_BYTES + 1] != 22 ||
                receivedPacket[expectedPacketSize - FRAME_FOOTER_BYTES + 2] != 33 ||
                receivedPacket[expectedPacketSize - FRAME_FOOTER_BYTES + 3] != 44) {
            return true;
        }
        validatedReceivedPacket = new byte[expectedPayloadSize];
        System.arraycopy(receivedPacket, FRAME_HEADER_BYTES, validatedReceivedPacket, 0,
                expectedPayloadSize);
        return true;
    }

    private void updateSendQueue() {
        if(isReadyToSend() && queuedPacket != null) {
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

    private int[] encodePacket(byte[] payload) {
        int packetBytes = (FRAME_HEADER_BYTES + payload.length + FRAME_FOOTER_BYTES);
        int packetBlocks = (packetBytes + ECC_DATA_BLOCK_BYTES - 1) / ECC_DATA_BLOCK_BYTES;
        byte[] framedPacket = new byte[packetBlocks * ECC_DATA_BLOCK_BYTES];
        int symbolCount = FRAME_PREAMBLE_SYMBOLS + INTERPACKET_GAP_SYMBOLS +
                ((packetBytes + ECC_DATA_BLOCK_BYTES - 1) / ECC_DATA_BLOCK_BYTES) *
                        ECC_CODE_BLOCK_SYMBOLS;
        int[] symbols = new int[symbolCount];
        int i = 0;

        framedPacket[0] = FRAME_SIGNATURE;
        framedPacket[1] = (byte)((payload.length >>> 0) & 0xFF);
        System.arraycopy(payload, 0, framedPacket, FRAME_HEADER_BYTES, payload.length);
        framedPacket[framedPacket.length - FRAME_FOOTER_BYTES + 0] = 11; // TODO CRC
        framedPacket[framedPacket.length - FRAME_FOOTER_BYTES + 1] = 22;
        framedPacket[framedPacket.length - FRAME_FOOTER_BYTES + 2] = 33;
        framedPacket[framedPacket.length - FRAME_FOOTER_BYTES + 3] = 44;

        for(int j = 0; j < FRAME_PREAMBLE_SYMBOLS - 1; ++j) {
            symbols[i++] = PREAMBLE_SYMBOL;
        }
        for(int j = 0; j < framedPacket.length; j += ECC_DATA_BLOCK_BYTES) {
            encodeOneBlock(framedPacket, j, symbols, i);
            i += ECC_CODE_BLOCK_SYMBOLS;
        }
        for(int j = 0; j < INTERPACKET_GAP_SYMBOLS; ++j) {
            symbols[i++] = INTERPACKET_GAP_SYMBOL;
        }

        System.err.print("Sending packet:");
        for(int j = 0; j < framedPacket.length; ++j) {
            System.err.print(" " + ((int)framedPacket[j] & 0xFF));
        }
        System.err.println();
        System.err.print("Symbols:");
        for(int j = 0; j < symbols.length; ++j) {
            System.err.print(" " + symbols[j]);
        }
        System.err.println();

        return symbols;
    }

    private void encodeOneBlock(byte[] input, int inputOffset, int[] output, int outputOffset) {
        int hamming0 = hammingCodes[(input[inputOffset + 0] >>> 0) & 0x0F];
        int hamming1 = hammingCodes[(input[inputOffset + 0] >>> 4) & 0x0F];
        int hamming2 = hammingCodes[(input[inputOffset + 1] >>> 0) & 0x0F];
        int hamming3 = hammingCodes[(input[inputOffset + 1] >>> 4) & 0x0F];
        for(int i = 0; i < 8; ++i) {
            int repackedBits0 = (((hamming0 >>> i) & 1) << 0) | (((hamming1 >>> i) & 1) << 1);
            int repackedBits1 = (((hamming2 >>> i) & 1) << 0) | (((hamming3 >>> i) & 1) << 1);

            output[outputOffset + i * 2 + 0] = repackedBits0 + 0 + ((i & 1) * 8);
            output[outputOffset + i * 2 + 1] = repackedBits1 + 4 + ((i & 1) * 8);
        }
    }

    private static class SymbolArrangement {
        private int[] symbols;
        private int symbolsConsumed;

        public int[] getSymbols() {
            return symbols;
        }

        public int getSymbolsConsumed() {
            return symbolsConsumed;
        }

        public SymbolArrangement(int[] symbols, int symbolsConsumed) {
            this.symbols = symbols;
            this.symbolsConsumed = symbolsConsumed;
        }
    }

    private int decodeOneBlock(int[] input, int[] inputTimes, int inputOffset, byte[] output,
                              int outputOffset) {
        SymbolArrangement[] symbolArrangements = enumerateSymbolArrangements(input, inputTimes, inputOffset);

        if(symbolArrangements == null || symbolArrangements.length == 0) {
            // Couldn't find any plausible arrangements of the symbols given
            return -1;
        }

        SymbolArrangement blockFound = null;
        byte[] trialBlock = new byte[ECC_DATA_BLOCK_BYTES];
        for(SymbolArrangement a : symbolArrangements) {
            if(tryDecodeOneBlock(a.getSymbols(), trialBlock)) {
                System.err.print("Decoded block:");
                for(int j = 0; j < trialBlock.length; ++j) {
                    System.err.print(" " + ((int)trialBlock[j] & 0xFF));
                }
                System.err.println();
                System.err.print("From symbols:");
                for(int j = 0; j < a.getSymbols().length; ++j) {
                    System.err.print(" " + a.getSymbols()[j]);
                }
                System.err.println();
                blockFound = a;
                break;
            }
        }

        if(blockFound != null) {
            System.arraycopy(trialBlock, 0, output, outputOffset, ECC_DATA_BLOCK_BYTES);
            return blockFound.getSymbolsConsumed();
        }

        // TODO guess missing data if possible in the case of lost symbols
        // This is sometimes possible because we can use agreement between our 4 hamming codes to
        // vote on a solution

        return -1;
    }

    public SymbolArrangement[] enumerateSymbolArrangements(int[] input, int[] inputTimes, int inputOffset) {
        ArrayList<SymbolArrangement> arrangements = new ArrayList<SymbolArrangement>();
        int[] currentArrangement = new int[ECC_CODE_BLOCK_SYMBOLS];
        enumerateSymbolArrangements(arrangements, currentArrangement, 0, 0, input, inputTimes,
                inputOffset);
        return arrangements.toArray(new SymbolArrangement[arrangements.size()]);
    }

    public void enumerateSymbolArrangements(ArrayList<SymbolArrangement> arrangements, int[] currentArrangement,
                                            int currentArrangementFilled, int missingSymbols,
                                            int[] input, int[] inputTimes, int inputOffset) {
        if(currentArrangementFilled >= ECC_CODE_BLOCK_SYMBOLS) {
            int[] arrangement = new int[ECC_CODE_BLOCK_SYMBOLS];
            System.arraycopy(currentArrangement, 0, arrangement, 0, arrangement.length);
            arrangements.add(new SymbolArrangement(arrangement, inputOffset));
            return;
        }

        int thisSym;
        int lastSymEnd, thisSymStart;
        if(inputOffset > 0) {
            lastSymEnd = inputTimes[inputOffset - 1] +
                    getLibrary().getFingerprintForSymbol(input[inputOffset - 1]).getMatchRows();
            if(inputOffset < input.length) {
                thisSym = input[inputOffset];
                thisSymStart = inputTimes[inputOffset];
                ++inputOffset;
            }
            else {
                thisSym = -1;
                thisSymStart = lastSymEnd + getLibrary().getMinCodeRows() * ECC_CODE_BLOCK_SYMBOLS;
            }
        }
        else {
            if(inputOffset < input.length) {
                thisSym = input[inputOffset];
                thisSymStart = inputTimes[inputOffset];
                lastSymEnd = thisSymStart;
                ++inputOffset;
            }
            else {
                return;
            }
        }

        int missingSymbolsTolerated = 4 - missingSymbols;
        int gap = thisSymStart - lastSymEnd;
        int minGapSymbols = Math.max(0, gap / (getLibrary().getMaxCodeRows() + GAP_EPSILON_ROWS));
        int maxGapSymbols = Math.min(Math.min(gap / getLibrary().getMinCodeRows(),
                missingSymbolsTolerated), ECC_CODE_BLOCK_SYMBOLS - currentArrangementFilled);
        if(minGapSymbols > maxGapSymbols) {
            return;
        }

        for(int i = minGapSymbols; i <= maxGapSymbols; ++i) {
            for(int j = 0; j < i; ++j) {
                currentArrangement[currentArrangementFilled + j] = -1;
            }
            if(currentArrangementFilled + i < ECC_CODE_BLOCK_SYMBOLS) {
                int sym = thisSym;
                if(sym != -1 && (sym / 4) % 4 != (currentArrangementFilled + i) % 4) {
                    sym = -1;
                }
                int newMissingSymbols = missingSymbols + i + (sym == -1 ? 1 : 0);
                if(newMissingSymbols <= missingSymbolsTolerated) {
                    currentArrangement[currentArrangementFilled + i] = sym;
                    enumerateSymbolArrangements(arrangements, currentArrangement,
                            currentArrangementFilled + i + 1, newMissingSymbols,
                            input, inputTimes, inputOffset);
                }
            }
            else {
                enumerateSymbolArrangements(arrangements, currentArrangement,
                        currentArrangementFilled + i, missingSymbols + i, input, inputTimes,
                        inputOffset);
            }
        }
    }

    private boolean tryDecodeOneBlock(int[] arrangedInput, byte[] output) {
        int hamming0 = 0;
        int hamming1 = 0;
        int hamming2 = 0;
        int hamming3 = 0;

        for(int i = 0; i < 8; ++i) {
            int repackedBits0 = arrangedInput[i * 2 + 0];
            int repackedBits1 = arrangedInput[i * 2 + 1];

            if(repackedBits0 < 0) {
                repackedBits0 = 0;
            }
            if(repackedBits1 < 0) {
                repackedBits1 = 0;
            }

            hamming0 |= ((repackedBits0 >>> 0) & 1) << i;
            hamming1 |= ((repackedBits0 >>> 1) & 1) << i;
            hamming2 |= ((repackedBits1 >>> 0) & 1) << i;
            hamming3 |= ((repackedBits1 >>> 1) & 1) << i;
        }

        int errPos0 = hammingCodeErrorPosition[hamming0];
        int errPos1 = hammingCodeErrorPosition[hamming1];
        int errPos2 = hammingCodeErrorPosition[hamming2];
        int errPos3 = hammingCodeErrorPosition[hamming3];

        if (errPos0 == -1 || errPos1 == -1 || errPos2 == -1 || errPos3 == -1) {
            // Uncorrectable 2-bit error
            return false;
        }

        if (errPos0 != -2 && errPos1 != -2 && errPos0 != errPos1) {
            // Conflicting notions of which bit is erroneous -- implies >2-symbol error
            return false;
        }

        if (errPos2 != -2 && errPos3 != -2 && errPos2 != errPos3) {
            // Conflicting notions of which bit is erroneous -- implies >2-symbol error
            return false;
        }

        hamming0 = hammingCodeCorrectedBits[hamming0];
        hamming1 = hammingCodeCorrectedBits[hamming1];
        hamming2 = hammingCodeCorrectedBits[hamming2];
        hamming3 = hammingCodeCorrectedBits[hamming3];

        if (hamming0 < 0 || hamming1 < 0 || hamming2 < 0 || hamming3 < 0) {
            // Uncorrectable error
            return false;
        }

        output[0] = (byte) (0 |
                (((hamming0 >>> 3) & 1) << 0) |
                (((hamming0 >>> 5) & 7) << 1) |
                (((hamming1 >>> 3) & 1) << 4) |
                (((hamming1 >>> 5) & 7) << 5));
        output[1] = (byte) (0 |
                (((hamming2 >>> 3) & 1) << 0) |
                (((hamming2 >>> 5) & 7) << 1) |
                (((hamming3 >>> 3) & 1) << 4) |
                (((hamming3 >>> 5) & 7) << 5));

        return true;
    }

}
