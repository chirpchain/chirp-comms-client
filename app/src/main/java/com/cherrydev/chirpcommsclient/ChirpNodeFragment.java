package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.graphics.Typeface;
import android.os.Bundle;
import android.app.ListFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;


import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.routeservice.BaseRouteServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.RouteServiceListener;
import com.cherrydev.chirpcommsclient.util.ChirpNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChirpNodeFragment extends ListFragment {
    private ChirpMainActivity activity;
    private RouteServiceListener routeServiceListener;
    public static ChirpNodeFragment newInstance(ChirpMessage message) {
        ChirpNodeFragment fragment = new ChirpNodeFragment();
        Bundle args = new Bundle();
        args.putSerializable("message", message);
        fragment.setArguments(args);
        return fragment;
    }

    private ChirpMessage getMessage() {
        return (ChirpMessage) getArguments().getSerializable("message");
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ChirpNodeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void populateList() {
        setListAdapter(new ArrayAdapter<ChirpNode>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, getAllOtherNodes()) {
            @Override
            public boolean isEnabled(int position) {
                return isNodeConnected(getItem(position));
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = activity.getLayoutInflater().inflate(R.layout.node_item, parent, false);
                }
                ChirpNode node = getItem(position);
                boolean isConnected = isNodeConnected(node);
                TextView name = (TextView) convertView.findViewById(R.id.chirp_node_name);
                View acousticImage = convertView.findViewById(R.id.chirp_node_acoustic_image);
                View socketImage = convertView.findViewById(R.id.chirp_node_socket_image);
                name.setText(node.getName() + "(" + node.getId() + ")" + (isConnected ? "" : " (disconnected)"));
                boolean showAcoustic = false;
                if (activity.getRouteService().getNodeInfo().isAcoustic()) {
                    if (node.isAcoustic()) {
                        showAcoustic = true;
                    }
                }
                if (showAcoustic) {
                    acousticImage.setVisibility(View.VISIBLE);
                    socketImage.setVisibility(View.GONE);
                }
                else {
                    acousticImage.setVisibility(View.GONE);
                    socketImage.setVisibility(View.VISIBLE);
                }
                return convertView;
            }
        });
    }

    private Set<Byte> getConnectedNodeIds() {
        return activity.getRouteService().getConnectedNodes();
    }

    private ChirpNode[] getAllOtherNodes() {
        if (activity == null) return new ChirpNode[0];
        if (activity.getRouteService() == null) return new ChirpNode[0];
        IntObjectMap<ChirpNode> allNodesMap = activity.getRouteService().getAllNodes();
        List<ChirpNode> allNodes = new ArrayList<>();
        int i = 0;
        for(IntObjectCursor<ChirpNode> node : allNodesMap) {
            if (node.value.getId() == activity.getRouteService().getNodeInfo().getId()) continue;
            allNodes.add(node.value);
        }
        return allNodes.toArray(new ChirpNode[allNodes.size()]);
    }

    private boolean isNodeConnected(ChirpNode node) {
        return getConnectedNodeIds().contains(node.getId());
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (ChirpMainActivity) activity;
        this.activity.getRouteService().addListener(routeServiceListener = new BaseRouteServiceListener() {
            @Override
            public void configChanged() {
                ((BaseAdapter) getListAdapter()).notifyDataSetInvalidated();
            }
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView headerText = new TextView(activity);
        headerText.setTextAppearance(activity, android.R.style.TextAppearance_Large);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setPadding(30,5,5,5);
        headerText.setText("Select a station");
        getListView().addHeaderView(headerText);
        populateList();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (routeServiceListener != null) this.activity.getRouteService().removeListener(routeServiceListener);
        routeServiceListener = null;
        this.activity = null;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        ChirpNode node = (ChirpNode) l.getAdapter().getItem(position);
        if (node != null) {
            getMessage().setTo(node.getId());
        }
        activity.getRouteService().submitNewMessage(getMessage());
        getFragmentManager().popBackStack();
    }

}
