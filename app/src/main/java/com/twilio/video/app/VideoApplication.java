package com.twilio.video.app;

import android.app.Application;

import com.twilio.video.app.BuildConfig;

import timber.log.Timber;

public class VideoApplication extends Application {
    public static final String HOCKEY_APP_ID = "11347c1df4dc4a929a1f6637fcbe64dc";

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

    }
}
