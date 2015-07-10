package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.app.Fragment;
import android.app.ListFragment;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.messagestore.MessageStoreListener;
import com.cherrydev.chirpcommsclient.messagestore.MessageStoreService;
import com.cherrydev.chirpcommsclient.routeservice.RouteService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import org.ocpsoft.prettytime.PrettyTime;

/**
 * Created by alannon on 2015-07-09.
 */
public class MessageListFragment extends ListFragment {
    private static final String TAG = "MessageListFragment";
    private static final PrettyTime prettyTime = new PrettyTime();
    private ChirpMainActivity activity;
    private CursorAdapter currentCursorAdaptor;

    private MessageStoreService messageStoreService;
    private ServiceBinding<MessageStoreListener, MessageStoreService> messageStoreServiceBinding;


    public static MessageListFragment newInstance() {
        return new MessageListFragment();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (ChirpMainActivity) activity;
        this.messageStoreServiceBinding = new ServiceBinding<MessageStoreListener, MessageStoreService>(activity, MessageStoreService.class) {
            @Override
            protected MessageStoreListener createListener() {
                return () -> {
                    Log.i(TAG, "Messages changed, changing the cursor");
                    if (currentCursorAdaptor != null) currentCursorAdaptor.changeCursor(getAllMessagesCursor());
                };
            }
        }.setOnConnect(s -> {
            messageStoreService = s;
            setupListAdapter();
        })
        .setOnDisconnect(() -> messageStoreService = null)
        .connect();
    }

    @Override
    public void onDetach() {
        if (currentCursorAdaptor != null) currentCursorAdaptor.getCursor().close();
        if (messageStoreServiceBinding != null) messageStoreServiceBinding.disconnect();
        super.onDetach();
    }

    private Cursor getAllMessagesCursor() {
        return messageStoreService.getAllMessagesByteDate(true);
    }

    private void setupListAdapter() {

        setListAdapter(currentCursorAdaptor = new CursorAdapter(activity, getAllMessagesCursor() , true) {

            private void setText(int resourceId, View view, String text) {
                ((TextView)view.findViewById(resourceId)).setText(text);
            }
            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                return activity.getLayoutInflater().inflate(R.layout.message_item, parent, false);
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                MessageStoreService.MessageDisplay m = messageStoreService.fromCursor(cursor);
                setText(R.id.message_item_id, view, "Id: " + m.id);
                setText(R.id.message_item_recipient, view, "To: " + m.to);
                setText(R.id.message_item_sender, view, "From: " + m.from);
                setText(R.id.message_item_message, view, m.message);
                setText(R.id.message_item_date, view, "Date: " + prettyTime.format(m.date));
            }
        });
    }

}
