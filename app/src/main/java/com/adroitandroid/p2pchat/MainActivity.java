package com.adroitandroid.p2pchat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.adroitandroid.near.connect.NearConnect;
import com.adroitandroid.near.discovery.NearDiscovery;
import com.adroitandroid.near.model.Host;
import com.adroitandroid.p2pchat.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.Contract;

import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class MainActivity extends AppCompatActivity {

    private static final long DISCOVERABLE_TIMEOUT_MILLIS = 60000;
    private static final long DISCOVERY_TIMEOUT_MILLIS = 10000;
    private static final long DISCOVERABLE_PING_INTERVAL_MILLIS = 5000;
    public static final String MESSAGE_REQUEST_START_CHAT = "start_chat";
    public static final String MESSAGE_RESPONSE_DECLINE_REQUEST = "decline_request";
    public static final String MESSAGE_RESPONSE_ACCEPT_REQUEST = "accept_request";
    private NearDiscovery mNearDiscovery;
    private NearConnect mNearConnect;
    private ActivityMainBinding binding;
    private Snackbar mDiscoveryInProgressSnackbar;
    private ParticipantsAdapter mParticipantsAdapter;
    private boolean mDiscovering;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mNearDiscovery = new NearDiscovery.Builder()
                .setContext(this)
                .setDiscoverableTimeoutMillis(DISCOVERABLE_TIMEOUT_MILLIS)
                .setDiscoveryTimeoutMillis(DISCOVERY_TIMEOUT_MILLIS)
                .setDiscoverablePingIntervalMillis(DISCOVERABLE_PING_INTERVAL_MILLIS)
                .setDiscoveryListener(getNearDiscoveryListener(), Looper.getMainLooper())
                .build();
        binding.startChattingBtn.setOnClickListener(v -> {
            if (mDiscovering) {
                stopDiscovery();
            } else {
                if (binding.handleEt.getText().length() > 0) {
                    mNearDiscovery.makeDiscoverable(binding.handleEt.getText().toString());
                    startDiscovery();
                    if (!mNearConnect.isReceiving()) {
                        mNearConnect.startReceiving();
                    }
                } else {
                    Snackbar.make(binding.getRoot(), "Please type in a handle first",
                            Snackbar.LENGTH_INDEFINITE).show();
                }
            }
        });

        mNearConnect = new NearConnect.Builder()
                .fromDiscovery(mNearDiscovery)
                .setContext(this)
                .setListener(getNearConnectListener(), Looper.getMainLooper())
                .build();

        mParticipantsAdapter = new ParticipantsAdapter(
            host -> mNearConnect.send(MESSAGE_REQUEST_START_CHAT.getBytes(), host));
        binding.participantsRv.setLayoutManager(new LinearLayoutManager(this));
        binding.participantsRv.setAdapter(mParticipantsAdapter);
    }

    @Contract(value = " -> new", pure = true)
    @NonNull
    private NearDiscovery.Listener getNearDiscoveryListener() {
        return new NearDiscovery.Listener() {

            @Override
            public void onPeersUpdate(@NotNull Set<? extends Host> hosts) {
                mParticipantsAdapter.setData((Set<Host>) hosts);
            }

            @Override
            public void onDiscoveryTimeout() {
                Snackbar.make(binding.getRoot(),
                        "No other participants found",
                        Snackbar.LENGTH_LONG).show();
                binding.discoveryPb.setVisibility(View.GONE);
                mDiscovering = false;
                binding.startChattingBtn.setText("Start Chatting");
            }

            @Override
            public void onDiscoveryFailure(Throwable e) {
                Snackbar.make(binding.getRoot(),
                        "Something went wrong while searching for participants",
                        Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onDiscoverableTimeout() {
                Toast.makeText(MainActivity.this, "You're not discoverable anymore", Toast.LENGTH_LONG).show();
            }
        };
    }

    @Contract(value = " -> new", pure = true)
    @NonNull
    private NearConnect.Listener getNearConnectListener() {
        return new NearConnect.Listener() {
            @Override
            public void onReceive(@NotNull byte[] bytes, @NotNull final Host sender) {
                switch (new String(bytes)) {
                    case MESSAGE_REQUEST_START_CHAT:
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(sender.getName() + " would like to start chatting with you.")
                                .setPositiveButton("Start", (dialog, which) -> {
                                    mNearConnect.send(MESSAGE_RESPONSE_ACCEPT_REQUEST.getBytes(), sender);
                                    stopNearServicesAndStartChat(sender);
                                })
                                .setNegativeButton("Cancel", (dialog, which) -> mNearConnect.send(MESSAGE_RESPONSE_DECLINE_REQUEST.getBytes(), sender)).create().show();
                        break;
                    case MESSAGE_RESPONSE_DECLINE_REQUEST:
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(sender.getName() + " is busy at the moment.")
                                .setNeutralButton("Ok", null).create().show();
                        break;
                    case MESSAGE_RESPONSE_ACCEPT_REQUEST:
                        stopNearServicesAndStartChat(sender);
                        break;
                }
            }

            @Override
            public void onSendComplete(long jobId) {

            }

            @Override
            public void onSendFailure(Throwable e, long jobId) {

            }

            @Override
            public void onStartListenFailure(Throwable e) {

            }
        };
    }

    private void stopNearServicesAndStartChat(Host sender) {
        mNearConnect.stopReceiving(true);
        mNearDiscovery.stopDiscovery();
        ChatActivity.start(MainActivity.this, sender);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mNearDiscovery.stopDiscovery();
        mNearConnect.stopReceiving(true);
    }

    private void stopDiscovery() {
        mNearDiscovery.stopDiscovery();
        mNearDiscovery.makeNonDiscoverable();

        mDiscovering = false;

        mDiscoveryInProgressSnackbar.dismiss();
        binding.participantsRv.setVisibility(View.GONE);
        binding.discoveryPb.setVisibility(View.GONE);
        binding.startChattingBtn.setText("Start Chatting");
    }

    private void startDiscovery() {
        mDiscovering = true;
        mNearDiscovery.startDiscovery();
        mDiscoveryInProgressSnackbar = Snackbar.make(binding.getRoot(), "Looking for chat participants",
                Snackbar.LENGTH_INDEFINITE);
        mDiscoveryInProgressSnackbar.show();
        mParticipantsAdapter.setData(new ArraySet<Host>());
        binding.participantsRv.setVisibility(View.VISIBLE);
        binding.discoveryPb.setVisibility(View.VISIBLE);
        binding.startChattingBtn.setText("Stop Searching");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mNearConnect.startReceiving();
    }
}
