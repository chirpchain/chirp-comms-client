package com.cherrydev.chirpcommsclient.chirpmodem;

import android.hardware.usb.UsbDevice;
import android.os.Environment;
import android.util.Log;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.cherrydev.chirpcommsclient.util.AudioConvert;
import com.cherrydev.usbaudiodriver.AudioPlayback;
import com.cherrydev.usbaudiodriver.KnownDevices;
import com.cherrydev.usbaudiodriver.UsbAudioDevice;
import com.cherrydev.usbaudiodriver.UsbAudioDeviceDescriptor;
import com.cherrydev.usbaudiodriver.UsbAudioDriver;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;

import ca.vectorharmony.chirpmodem.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class UsbAudioReceiver extends AudioReceiver {

    private static boolean isInitted;

    private static IntObjectMap<UsbAudioDevice> portDevices = new IntObjectHashMap<>();
    private static IntObjectMap<UsbAudioReceiver> portReceivers = new IntObjectHashMap<>();
    private static final String TAG = "UsbAudioReceiver";
    public static void initUsbAudioDevices() {
        if (isInitted) return;
        UsbDevice[] rawUsbDevices = UsbAudioDriver.getAttachedAudioDevices();
        ArrayList<UsbAudioDevice> audioDevices = new ArrayList<>();
        for (UsbDevice rawUsbDevice : rawUsbDevices) {
            Log.d(TAG, rawUsbDevice.getDeviceName());
            UsbAudioDeviceDescriptor deviceDescriptor = KnownDevices.findByVenderProduct(rawUsbDevice.getVendorId(), rawUsbDevice.getProductId());
            if (deviceDescriptor == null) {
                Log.d(TAG, "Found unknown device " + rawUsbDevice.getDeviceName());
            }
            final UsbAudioDevice audioDevice = new UsbAudioDevice(deviceDescriptor, rawUsbDevice.getDeviceName(), (device, data, length) -> {
                writeToPort(data, length, device.getPort());
            });
            portDevices.put(audioDevice.getPort(), audioDevice);
        }


        isInitted = true;
    }

    private static void writeToPort(byte[] data, int length, int port) {
        portReceivers.get(port).writeToBuffer(data, length);
    }

    private int port;
    private int nativeSampleRate;
    private int sampleRate;
    private ByteBuffer byteBuffer;
    private short[] tmpShortBuffer;
    private volatile int dropped;
    private UsbAudioDevice device;

    private synchronized void writeToBuffer(byte[] data, int length) {
        int remaining = byteBuffer.remaining();
        if (length > remaining) {
            dropped += length - remaining;
            length = remaining;
        }
        byteBuffer.put(data, 0, length);
    }

    public boolean initOnThisThread(boolean isRightChannel, int sampleRate) {
        this.sampleRate = sampleRate;
        if (portDevices.size() == 0){
            Log.w(TAG, "No devices found");
            return false;
        }
        UsbAudioDevice device;
        // Right devices has the higher port
        if (portDevices.size() == 2) {
            Iterator<IntObjectCursor<UsbAudioDevice>> c = portDevices.iterator();
            UsbAudioDevice first = c.next().value;
            UsbAudioDevice second = c.next().value;
            int port;
            if (isRightChannel) {
                 port = Math.max(first.getPort(), second.getPort());
            }
            else {
                port = Math.min(first.getPort(), second.getPort());
            }
            device = portDevices.get(port);
        }
        else {
            Iterator<IntObjectCursor<UsbAudioDevice>> c = portDevices.iterator();
            device = c.next().value;
        }
        port = device.getPort();
        portReceivers.put(port, this);
        device = portDevices.get(port);
        this.nativeSampleRate = device.getDescriptor().sampleRate;
        byteBuffer = ByteBuffer.allocateDirect(this.nativeSampleRate); // 1s
        tmpShortBuffer = new short[byteBuffer.capacity() / 2];
        if (sampleRate != nativeSampleRate && sampleRate != (nativeSampleRate / 2)) {
            Log.e(TAG, "Sample rate != nativeSampleRate or nativeSampleRate / 2");
            return false;
        }
        return device.beginReadAudio();
    }

    @Override
    public int getAndResetDroppedSampleCount() {
        int dropped = this.dropped;
        this.dropped = 0;
        return dropped;
    }

    @Override
    public synchronized float[] readAudioBuffer() {
        byte[] bufBytes = byteBuffer.array();
        ByteBuffer.wrap(bufBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(tmpShortBuffer);
        int samplesReceived = byteBuffer.position() / 2;
        byteBuffer.clear();
        if (sampleRate == (nativeSampleRate / 2)) {

            // throw away half the samples in-place
            for (int i = 0; i < samplesReceived; i += 2) {
                tmpShortBuffer[i / 2] = tmpShortBuffer[i];
            }
            return AudioConvert.convertToFloat(tmpShortBuffer, 0, samplesReceived / 2);
        }
        else {
            return AudioConvert.convertToFloat(tmpShortBuffer, 0, samplesReceived);
        }
    }

    @Override
    public void stop() {
        device.close();
    }
}
