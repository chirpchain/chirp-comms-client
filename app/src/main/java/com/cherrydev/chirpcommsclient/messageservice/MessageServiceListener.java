package com.cherrydev.chirpcommsclient.messageservice;

import com.cherrydev.chirpcommsclient.messages.ChirpBinaryMessage;


public interface MessageServiceListener {
    void receiveChirpMessage(ChirpBinaryMessage m);
}
