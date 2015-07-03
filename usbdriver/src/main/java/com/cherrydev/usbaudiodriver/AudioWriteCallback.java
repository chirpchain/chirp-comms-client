package com.cherrydev.usbaudiodriver;

/**
 * Created by alannon on 2015-06-21.
 */
public interface AudioWriteCallback {
    void write(UsbAudioDevice device, byte[] data, int length);
}
