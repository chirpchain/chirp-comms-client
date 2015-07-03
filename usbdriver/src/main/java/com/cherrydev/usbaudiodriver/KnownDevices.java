package com.cherrydev.usbaudiodriver;

/**
 * Created by alannon on 2015-06-21.
 */
public class KnownDevices {
    public static final UsbAudioDeviceDescriptor LOGITEC_MIC = new UsbAudioDeviceDescriptor(
            "Logitec Mic", 0x046d, 0x0a03, 0x82, 96, 1, 48000, (byte) 8, (char) 0x7FFF
    );
    public static final UsbAudioDeviceDescriptor GENERIC_ADAPTOR = new UsbAudioDeviceDescriptor(
            "Generic Adaptor", 0x0d8c, 0x013c, 0x82, 100, 2, 48000, (byte) 10, (char) 0x7FFF
    );
    public static final UsbAudioDeviceDescriptor XLR_ADAPTOR = new UsbAudioDeviceDescriptor(
            "XLR Mic Adaptor", 0x0d8c, 0x0139, 0x82, 100, 2, 48000, (byte) 10, (char) 0x7FFF
    );

    public static UsbAudioDeviceDescriptor[] getAll() {
        return new UsbAudioDeviceDescriptor[]{LOGITEC_MIC, GENERIC_ADAPTOR, XLR_ADAPTOR};
    }

    public static UsbAudioDeviceDescriptor findByVenderProduct(int venderId, int productId) {
        UsbAudioDeviceDescriptor[] all = getAll();
        for (int i = 0; i < all.length; i++) {
            UsbAudioDeviceDescriptor d = all[i];
            if (d.venderId == venderId && d.productId == productId) return d;
        }
        return null;
    }
}
