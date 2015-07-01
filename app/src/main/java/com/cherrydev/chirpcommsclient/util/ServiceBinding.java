package com.cherrydev.chirpcommsclient.util;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java8.util.function.Consumer;

public abstract class ServiceBinding<TListener, TService extends BaseService<TListener>> {
    private Context context;
    private Intent serviceIntent;

    private ServiceConnection serviceConnection;
    private TService service;
    private TListener serviceListener;
    private Consumer<TService> onConnect;
    private Runnable onDisconnect;

    protected ServiceBinding(Context context, Class<TService> serviceClazz) {
        this.context = context;
        this.serviceIntent = new Intent(context, serviceClazz);
    }

    public ServiceBinding<TListener, TService> setOnConnect(Consumer<TService> onConnect) {
        this.onConnect = onConnect;
        return this;
    }

    public ServiceBinding<TListener, TService> setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
        return this;
    }

    public TService getService() {
        return service;
    }

    public ServiceBinding<TListener, TService> connect() {
        context.startService(serviceIntent);
        context.bindService(serviceIntent, serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                //noinspection unchecked
                service = (TService) ((BaseService.LocalBinder)binder).getService();
                onConnect.accept(service);
                serviceListener = createListener();
                service.addListener(serviceListener);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (serviceListener != null) service.removeListener(serviceListener);
                service = null;
                serviceListener = null;
                serviceConnection = null;
                onDisconnect.run();
            }
        }, 0);
        return this;
    }

    public void disconnect() {
        if (serviceListener != null) {
            service.removeListener(serviceListener);
            serviceListener = null;
        }
        if (serviceConnection != null) {
            context.unbindService(serviceConnection);
            service = null;
            serviceConnection = null;
        }
    }

    protected abstract TListener createListener();
}
