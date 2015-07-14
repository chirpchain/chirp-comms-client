package com.cherrydev.chirpcommsclient.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.os.Process;

import com.cherrydev.chirpcommsclient.ChirpMainActivity;

/**
 * Created by alannon on 2015-07-10.
 */
public class RestartSelf {
    public static void restartSelf(Context context) {
        AlarmManager alm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alm.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, PendingIntent.getActivity(context, 0, new Intent(context, ChirpMainActivity.class), 0));
        Process.killProcess(Process.myPid());
    }
}
