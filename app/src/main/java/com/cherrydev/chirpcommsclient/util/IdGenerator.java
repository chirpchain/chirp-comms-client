package com.cherrydev.chirpcommsclient.util;

import java.util.Random;

public class IdGenerator {

    private static byte highByte;
    private static final Random random = new Random();

    public static void setHighByte(byte highByte) {
        IdGenerator.highByte = highByte;
    }

    public static int generate() {
        // For now, just random...
        int id = random.nextInt();
        return id | (highByte << 24);
    }
}
