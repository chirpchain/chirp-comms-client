package com.cherrydev.chirpcommsclient.messagestore;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.messageservice.MessageServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.BaseRouteServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.RouteService;
import com.cherrydev.chirpcommsclient.routeservice.RouteServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import java.util.Date;

import static com.cherrydev.chirpcommsclient.messagestore.ChirpMessageStoreHelper.*;

public class MessageStoreService extends BaseService<MessageStoreListener> {
    private RouteService routeService;
    private ServiceBinding<RouteServiceListener, RouteService> routeServiceBinding;
    private SQLiteDatabase db;

    public MessageStoreService() {
    }

    public static class MessageDisplay {
        public final String from;
        public final String to;
        public final int id;
        public final Date date;
        public final String message;

        public MessageDisplay(int id, String from, String to, String message, Date date) {
            this.from = from;
            this.to = to;
            this.id = id;
            this.date = date;
            this.message = message;
        }
    }

    @Override
    protected void onStartup() {
        db = new ChirpMessageStoreHelper(this).getWritableDatabase();
        routeServiceBinding = new ServiceBinding<RouteServiceListener, RouteService>(this, RouteService.class) {
            @Override
            protected RouteServiceListener createListener() {
                return new BaseRouteServiceListener() {
                    @Override
                    public void chirpReceived(ChirpMessage message) {
                        writeMessage(message);
                    }
                };
            }
        }.setOnConnect(s -> this.routeService = s)
        .setOnDisconnect(() -> this.routeService = null)
        .connect();
    }

    public MessageDisplay fromCursor(Cursor c) {
        return new MessageDisplay(
                c.getInt(c.getColumnIndex(COLUMN_ID)),
                c.getString(c.getColumnIndex(COLUMN_SENDER)),
                c.getString(c.getColumnIndex(COLUMN_RECIPIENT)),
                c.getString(c.getColumnIndex(COLUMN_MESSAGE)),
                new Date(c.getLong(c.getColumnIndex(COLUMN_DATE)))
                );
    }

    public void writeMessage(ChirpMessage m) {
        long when = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put(COLUMN_ID, m.getMessageId());
        v.put(COLUMN_SENDER, m.getSender());
        v.put(COLUMN_RECIPIENT, m.getRecipient());
        v.put(COLUMN_MESSAGE, m.getMessage());
        v.put(COLUMN_DATE, when);
        db.insert(TABLE_MESSAGES, null, v);
        forEachListener(l -> l.messagesChanged());
    }

    public Cursor getAllMessagesByteDate(boolean descending) {
        return db.query(TABLE_MESSAGES, null, null, null, null, null, COLUMN_DATE + (descending ? " DESC" : ""));
    }

    @Override
    public void onDestroy() {
        db.close();
        if (routeServiceBinding != null) routeServiceBinding.disconnect();
        super.onDestroy();
    }

    @Override
    protected void handleListenerException(Throwable e) {

    }
}
