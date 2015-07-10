package com.cherrydev.chirpusbdriverdemo;

import android.app.Application;
import android.util.Log;

import com.cherrydev.usbaudiodriver.UsbAudioDriver;

/**
 * Created by alannon on 2015-06-21.
 */
public class UsbDemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        /*
        if (!UsbAudioDriver.initWithContext(this)) {
            Log.w("UsbAudio", "Couldn't init audio driver");
        }*/
        Log.w("UsbAudio", "Not trying to init audio driver");
    }
}
