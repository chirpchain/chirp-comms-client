package com.cherrydev.chirpusbdriverdemo;

import android.app.Application;

import com.cherrydev.usbaudiodriver.UsbAudioDriver;

/**
 * Created by alannon on 2015-06-21.
 */
public class UsbDemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        UsbAudioDriver.initWithContext(this);
    }
}
