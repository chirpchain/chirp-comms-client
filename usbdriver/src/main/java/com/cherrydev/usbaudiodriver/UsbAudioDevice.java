package com.cherrydev.usbaudiodriver;

/**
 * Created by alannon on 2015-06-20.
 */
public class UsbAudioDevice {
    private static final String pathPrefix = "/dev/bus/usb/";

    final UsbAudioDeviceDescriptor descriptor;
    final String deviceName;
    final byte[] portPath;

    long devicePtr;

    private final AudioWriteCallback callback;
    private boolean open;
    private byte port;
    private char volume;

    public UsbAudioDevice(UsbAudioDeviceDescriptor descriptor, String deviceName, AudioWriteCallback callback) {
        this.descriptor = descriptor;
        this.deviceName = deviceName;
        this.callback = callback;
        if (!deviceName.startsWith(pathPrefix)) {
            throw new IllegalStateException("Device name doesn't start with /dev/bus/usb!");
        }
        deviceName = deviceName.substring(pathPrefix.length());
        String[] pathComponents = deviceName.split("/");// ["002","001"]
        portPath = new byte[pathComponents.length];
        for (int i = 0; i < pathComponents.length; i++) {
            portPath[i] = Byte.parseByte(pathComponents[i]);
        }
        open();
    }

    public void open() {
        if (devicePtr != 0) return;
        devicePtr = UsbAudioDriver.openDevice(this);
        if (devicePtr == 0) {
            throw new RuntimeException("Native driver couldn't open the device!");
        }
        port = UsbAudioDriver.getPort(this);
        volume = UsbAudioDriver.setVolume(this, descriptor.volume);
        UsbAudioDriver.setSampleRate(this, descriptor.sampleRate);
    }

    public void close() {
        UsbAudioDriver.closeDevice(this);
        devicePtr = 0;
    }

    public boolean isOpen() {
        return devicePtr != 0;
    }

    public boolean beginReadAudio() {
        return UsbAudioDriver.beginReadAudio(this);
    }

    public byte getPort() {
        return port;
    }

    public char getVolume() {
        return volume;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public UsbAudioDeviceDescriptor getDescriptor() {
        return descriptor;
    }

    void write(byte[] decodedAudio, int bufLength) {
        callback.write(this, decodedAudio, bufLength);
    }


}

