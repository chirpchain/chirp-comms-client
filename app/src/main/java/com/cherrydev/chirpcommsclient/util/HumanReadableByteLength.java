package com.cherrydev.chirpcommsclient.util;


public class HumanReadableByteLength {
    // https://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    // Perhaps the cleverest code I've seen all day:
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
