package com.cherrydev.chirpcommsclient.routeservice;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;

public interface RouteServiceListener {
    void chirpReceived(ChirpMessage message);
    void configChanged();
}
