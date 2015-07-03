package com.cherrydev.usbaudiodriver;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class UsbAudioDriver {
    private static final String LOG_TAG = "UsbAudioDriver";

    private static final UsbAudioDevice[] openDevices = new UsbAudioDevice[255];
    private static UsbManager usbManager;
    private static Context context;
    private static int uid;
    private static boolean runningLoop = false;

    static {
        System.loadLibrary("usbaudio");
        if (!initDriver(UsbAudioDriver.class, "write")) {
            throw new RuntimeException("Error initializing driver");
        }
    }

    public static void initWithContext(Context c) {
        context = c;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        try {
            uid = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).uid;
        }
        catch(PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static UsbDevice[] getAttachedAudioDevices() {
        Collection<UsbDevice> devices = usbManager.getDeviceList().values();
        ArrayList<UsbDevice> audioDevices = new ArrayList<>();
        for(UsbDevice d : devices) {
            if(d.getDeviceClass() == UsbConstants.USB_CLASS_AUDIO) {
                audioDevices.add(d);
            }
            else if(d.getDeviceClass() == 0) {
                // Enumerate interfaces to look
                for(int i = 0; i < d.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = d.getInterface(i);
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                        audioDevices.add(d);
                        break;
                    }
                }
            }
        }
        return audioDevices.toArray(new UsbDevice[audioDevices.size()]);
    }

    /**
     *
     * @param callbackClass A class with the method
     * @param callbackMethod The name of a method with the signature:void foo(byte[] data, int dataLength, int tag)
     * @return
     */
    private static native boolean initDriver(Class<?> callbackClass, String callbackMethod);
    private static native boolean loop();
    private static native long setupDevice(byte[] portPath, int endPoint, int packetSize, int interfaceNum, int tag, byte controlUnitId);
    private static native void closeDevice(long devHandle);
    private static native boolean beginReadAudio(long devHandle);
    private static native byte getPort(long devHandle);
    private static native char setVolume(long devHandle, char volume);
    private static native int setSampleRate(long devHandle, int rate);
    @SuppressWarnings("unused")
    public static void write(byte[] audioData, int bufLength, int tag) {
        if (openDevices[tag] != null) {
            openDevices[tag].write(audioData, bufLength);
        }
    }

    private static void runLoop() {
        if (! runningLoop) {
            runningLoop = true;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (loop()) {
                        ;
                    }
                }
            }, "USB Audio Loop");
            t.setDaemon(true);
            t.start();
        }
    }

    static void closeDevice(UsbAudioDevice device) {
        closeDevice(device.devicePtr);
        for (int i = 0; i < openDevices.length; i++) {
            if (openDevices[i] == device) {
                openDevices[i] = null;
                return;
            }
        }
        throw new IllegalStateException("That device was not registered with the driver!!");
    }

    static long openDevice(UsbAudioDevice device) {
        int tag = -1;
        for (int i = 0; i < openDevices.length; i++) {
            if (openDevices[i] == null) {
                tag = i;
                break;
            }
        }
        if (tag == -1) {
            throw new RuntimeException("Ran out of devices!!");
        }
        openDevices[tag] = device;
        try {
            List<String> result = Shell.SU.run(String.format("chgrp %d %s", uid, device.deviceName));
            if (result == null) {
                String msg = String.format("Couldn't set group id of usb device %s to %d", device.deviceName, uid);
                Log.e(LOG_TAG, msg);
                throw new SecurityException(msg);
            }
            return setupDevice(device.portPath, device.descriptor.endpointAddr, device.descriptor.maxPacketSize, device.descriptor.audioInterfaceNum, tag, device.descriptor.controlUnitId);
        }
        catch (RuntimeException e) {
            openDevices[tag] = null;
            throw e;
        }
    }

    static byte getPort(UsbAudioDevice device) {
        return getPort(device.devicePtr);
    }

    static char setVolume(UsbAudioDevice device, char volume) {
        return setVolume(device.devicePtr, volume);
    }

    static int setSampleRate(UsbAudioDevice device, int rate) {
        return setSampleRate(device.devicePtr, rate);
    }


    static boolean beginReadAudio(UsbAudioDevice device) {
        runLoop();
        return beginReadAudio(device.devicePtr);
    }

}
