package com.cherrydev.chirpcommsclient.util;

import java.util.TimerTask;

/**
 * Created by alannon on 2015-07-11.
 */
public class RunnableTimerTask extends TimerTask {
    private final Runnable runnable;
    public RunnableTimerTask(Runnable taskRunnable) {
        this.runnable = taskRunnable;
    }
    @Override
    public void run() {
        runnable.run();
    }
}
