package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import butterknife.OnFocusChange;
import butterknife.OnTextChanged;


/**
 * A placeholder fragment containing a simple view.
 */
public class ChirpEnterMessageFragment extends Fragment {
    private static final int TEXT_ENTER_TIMEOUT = 120000; // 2m
    private final Handler handler = new Handler();
    private Runnable textEnterTimeout = () -> {
        {
            closeSoftKeyboard();
            scheduleTextTimeout();
        }
    };

    @Bind(R.id.messageText)
    TextView messageText;

    @Bind(R.id.message_from_text)
    TextView fromText;

    @Bind(R.id.message_to_text)
    TextView toText;

    public ChirpEnterMessageFragment() {
    }

    public static ChirpEnterMessageFragment newInstance() {
        ChirpEnterMessageFragment fragment = new ChirpEnterMessageFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chirp_enter_message, container, false);
        ButterKnife.bind(this, v);
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override
    public void onStart() {
        super.onStart();
        scheduleTextTimeout();
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(textEnterTimeout);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @OnFocusChange({R.id.messageText, R.id.message_to_text, R.id.message_from_text})
    public void focused(boolean gained) {
        if (gained) scheduleTextTimeout();
    }

    @OnTextChanged({R.id.messageText, R.id.message_to_text, R.id.message_from_text})
    public void typed() {
        scheduleTextTimeout();
    }

    @OnEditorAction(R.id.messageText)
    public boolean onMessageTextAction(int actionId) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            onSendMessage();
            return true;
        }
        return false;
    }

    @OnClick(R.id.messageSendButton)
    public void onSendButtonClick() {
        onSendMessage();
    }

    private void showContentWarning(String warning) {
        Toast.makeText(getActivity(), warning, Toast.LENGTH_SHORT).show();
    }

    private boolean checkContents() {
        if (messageText.getText().toString().trim().length() == 0) {
            showContentWarning("Oops, please enter a message!");
            messageText.requestFocus();
            return false;
        }
        return true;
    }

    private void closeSoftKeyboard() {
        InputMethodManager imm =  (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = getActivity().getCurrentFocus();
        if (focus != null && focus instanceof EditText) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void onSendMessage() {
        if (!checkContents()) return;
        ChirpMessage chirpMessage = new ChirpMessage();
        chirpMessage.setMessage(messageText.getText().toString().trim());
        chirpMessage.setSender(fromText.getText().toString().trim());
        chirpMessage.setRecipient(toText.getText().toString().trim());
        messageText.setText("");
        fromText.setText("");
        toText.setText("");
        closeSoftKeyboard();
        ChirpNodeFragment f = ChirpNodeFragment.newInstance(chirpMessage);
        FragmentTransaction fa = getFragmentManager().beginTransaction();
        fa.addToBackStack("gotoNodeList");
        fa.replace(R.id.fragment_message_container, f);
        fa.commit();
    }

    private void scheduleTextTimeout() {
        handler.removeCallbacks(textEnterTimeout);
        handler.postDelayed(textEnterTimeout, TEXT_ENTER_TIMEOUT);
    }
}
