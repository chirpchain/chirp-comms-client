package com.cherrydev.chirpcommsclient.util;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public abstract class ServiceBinding<TListener, TService extends BaseService<TListener>> {
    private Context context;
    private Intent serviceIntent;

    private ServiceConnection serviceConnection;
    private TService service;
    private TListener serviceListener;
    private OnConnect<TService> onConnect;
    private Runnable onDisconnect;

    protected ServiceBinding(Context context, Class<TService> serviceClazz) {
        this.context = context;
        this.serviceIntent = new Intent(context, serviceClazz);
    }

    public void setOnConnect(OnConnect<TService> onConnect) {
        this.onConnect = onConnect;
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public TService getService() {
        return service;
    }

    public void connect() {
        context.startService(serviceIntent);
        context.bindService(serviceIntent, serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                //noinspection unchecked
                ServiceBinding.this.service = (TService) ((BaseService.LocalBinder)service).getService();
                ServiceBinding.this.serviceListener = createListener();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
                serviceListener = null;
                serviceConnection = null;
            }
        }, 0);
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

    public interface OnConnect<TService> {
        void onConnect(TService service);
    }
}
