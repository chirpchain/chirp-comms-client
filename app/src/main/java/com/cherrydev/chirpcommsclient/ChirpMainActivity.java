package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.routeservice.BaseRouteServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.RouteService;
import com.cherrydev.chirpcommsclient.routeservice.RouteServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import butterknife.Bind;
import butterknife.ButterKnife;


public class ChirpMainActivity extends Activity {

    private ServiceBinding<RouteServiceListener, RouteService> routeServiceBinding;
    private RouteService routeService;
    private Handler handler = new Handler();

    @Bind(R.id.fragment_message_container)
    FrameLayout messageContainer;

    @Bind(R.id.fragment_blinky_lights_container)
    FrameLayout blinkyLightsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chirp_main);
        ButterKnife.bind(this);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.fragment_message_container, ChirpEnterMessageFragment.newInstance());
        ft.add(R.id.fragment_blinky_lights_container, BlinkyLightsFragment.newInstance());
        ft.commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectServices();
    }

    @Override
    protected void onStop() {
        disconnectServices();
        super.onStop();
    }

    RouteService getRouteService() {
        return routeService;
    }

    public void switchToCreateMessage() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.fragment_message_container, ChirpEnterMessageFragment.newInstance());
        ft.commit();
    }

    public void switchToShowMessages() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.fragment_message_container, MessageListFragment.newInstance());
        ft.commit();
    }

    private void connectServices() {
        routeServiceBinding = new ServiceBinding<RouteServiceListener, RouteService>(this, RouteService.class) {
            @Override
            protected RouteServiceListener createListener() {
                return new BaseRouteServiceListener() {
                    @Override
                    public void chirpReceived(ChirpMessage message) {
                        handler.post(() -> Toast.makeText(ChirpMainActivity.this, "Incoming: " + message.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void configChanged() {
                        handler.post(() -> Toast.makeText(ChirpMainActivity.this, "My name is " + routeService.getNodeInfo().getName(), Toast.LENGTH_LONG).show());
                    }
                };
            }
        }
                .setOnConnect(s -> this.routeService = s)
                .setOnDisconnect(() -> this.routeService = null)
                .connect();
    }

    private void disconnectServices() {
        routeServiceBinding.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_chirp_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
