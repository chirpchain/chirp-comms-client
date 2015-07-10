package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cherrydev.chirpcommsclient.routeservice.BaseRouteServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.RouteService;
import com.cherrydev.chirpcommsclient.routeservice.RouteServiceListener;
import com.cherrydev.chirpcommsclient.util.ChirpNode;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class BlinkyLightsFragment extends Fragment {

    private ChirpMainActivity activity;
    private RouteService routeService;
    private ServiceBinding<RouteServiceListener, RouteService> routeserviceBinding;

    @Bind(R.id.node_name)
    TextView nodeName;

    public static BlinkyLightsFragment newInstance() {
        BlinkyLightsFragment fragment = new BlinkyLightsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_blinky_lights, container, false);
        ButterKnife.bind(this, v);
        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (ChirpMainActivity) activity;
        routeserviceBinding = new ServiceBinding<RouteServiceListener, RouteService>(activity, RouteService.class) {
            @Override
            protected RouteServiceListener createListener() {
                return new BaseRouteServiceListener() {
                    @Override
                    public void configChanged() {
                        updateRouteConfig();
                    }
                };
            }
        }.setOnConnect(s -> {
            routeService = s;
            updateRouteConfig();
        })
        .setOnDisconnect(() -> routeService = null)
        .connect();

    }

    @Override
    public void onDetach() {
        routeserviceBinding.disconnect();
        this.activity = null;
        super.onDetach();
    }

    private void updateRouteConfig() {
        if (routeService == null) return;
        ChirpNode nodeInfo = routeService.getNodeInfo();
        if (nodeInfo != null) {
            nodeName.setText(String.format("%s (%d)", nodeInfo.getName(), nodeInfo.getId()));
        }
    }

    @OnClick(R.id.create_message_button)
    public void onClickCreateMessage() {
        activity.switchToCreateMessage();
    }

    @OnClick(R.id.show_messages_button)
    public void onClickShowMessages() {
        activity.switchToShowMessages();
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }
}
