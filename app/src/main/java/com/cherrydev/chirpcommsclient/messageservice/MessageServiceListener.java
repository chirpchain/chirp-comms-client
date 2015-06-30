package com.cherrydev.chirpcommsclient.messageservice;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;


public interface MessageServiceListener {
    void receiveChirpMessage(ChirpMessage m);
}
