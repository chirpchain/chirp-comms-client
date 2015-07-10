package com.cherrydev.chirpcommsclient.util;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseService<TListener> extends Service {
    private Set<TListener> mListeners = new HashSet<>();
    private boolean hasStarted;
    private final IBinder mBinder = new LocalBinder(this);

    public static class LocalBinder extends Binder {
        private Service service;
        public LocalBinder(Service service) {
            this.service = service;
        }
        public Service getService() {
            return service;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (! hasStarted) {
            hasStarted = true;
            onStartup();
        }
        return START_STICKY;
    }

    protected abstract void onStartup();



    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void addListener(TListener listener) {
        if (listener != null)  mListeners.add(listener);
    }

    public void removeListener(TListener listener) {
        if (listener != null) mListeners.remove(listener);
    }

    protected void forEachListener(EachListenerAction<TListener> action) {
        for(TListener listener : mListeners) {
            try {
                action.eachListener(listener);
            }
            catch (Exception e) {
                handleListenerException(e);
            }
        }
    }

    protected abstract void handleListenerException(Throwable e);

    protected interface EachListenerAction<TListener> {
        void eachListener(TListener listener);
    }
}
