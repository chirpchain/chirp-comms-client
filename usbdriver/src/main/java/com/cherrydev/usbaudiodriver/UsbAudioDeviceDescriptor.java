package com.cherrydev.usbaudiodriver;

/**
 * Contains a description of a (potentially) supported audio device
 * All of this information can be read using the linux command lsusb -vvv
 */
public class UsbAudioDeviceDescriptor {
    /**
     * A human-readable device name
     */
    public final String name;
    /**
     * Vender ID
     */
    public final int venderId;
    /**
     * Product ID
     */
    public final int productId;
    /**
     * Endpoint Address, usually 0x82 for "IN"
     */
    public final int endpointAddr;
    /**
     * Maximum size of packet the device may send
     */
    public final int maxPacketSize;
    /**
     * The number/index of the audio interface on the device.  This is the interface that contains the endpoint.
     */
    public final int audioInterfaceNum;
    /**
     * The sample rate the device will be set to
     */
    public final int sampleRate;
    public final byte controlUnitId;
    public final char volume;

    public UsbAudioDeviceDescriptor(String name, int venderId, int productId, int endpointAddr, int maxPacketSize, int audioInterfaceNum, int sampleRate, byte controlUnitId, char volume) {
        this.name = name;
        this.venderId = venderId;
        this.productId = productId;
        this.endpointAddr = endpointAddr;
        this.maxPacketSize = maxPacketSize;
        this.audioInterfaceNum = audioInterfaceNum;
        this.sampleRate = sampleRate;
        this.controlUnitId = controlUnitId;
        this.volume = volume;
    }



}
