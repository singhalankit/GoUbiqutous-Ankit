package com.example.android.sunshine.app.sync;

/**
 * Created by ANKIT_PC on 18-12-2016.
 */
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WearableRequestListenerService extends WearableListenerService {

    GoogleApiClient mGoogleApiClient;
    private static final String requestDataPath = "/wearable-request-path";
    private static final String LOG_TAG = WearableRequestListenerService.class.getSimpleName();

    public WearableRequestListenerService() {
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.v(LOG_TAG, "Received a message");

        if(messageEvent.getPath().equals(requestDataPath)) {
            Log.v(LOG_TAG, "we received a message from the wearable to fetch data and send it over");

            Context context = WearableRequestListenerService.this.getApplicationContext();
            SunshineSyncAdapter.syncImmediately(context);
        }
    }
}

