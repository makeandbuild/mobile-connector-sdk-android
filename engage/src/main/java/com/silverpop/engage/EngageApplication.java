package com.silverpop.engage;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import com.silverpop.engage.config.EngageConfig;
import com.silverpop.engage.config.EngageConfigManager;
import com.silverpop.engage.deeplinking.EngageDeepLinkManager;
import com.silverpop.engage.domain.UBF;
import com.silverpop.engage.domain.XMLAPI;
import com.silverpop.engage.location.manager.EngageLocationManager;
import com.silverpop.engage.location.manager.plugin.EngageLocationManagerDefault;
import com.silverpop.engage.util.EngageExpirationParser;

import org.mobiledeeplinking.android.Handler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jeremydyer on 6/5/14.
 */
public class EngageApplication
        extends Application {

    private static final String TAG = EngageApplication.class.getName();

    public static final String CLIENT_ID_META = "ENGAGE_CLIENT_ID";
    public static final String CLIENT_SECRET_META = "ENGAGE_CLIENT_SECRET_META";
    public static final String REFRESH_TOKEN_META = "ENGAGE_REFRESH_TOKEN";
    public static final String HOST = "ENGAGE_HOST";


    @Override
    public void onCreate() {
        super.onCreate();

        EngageManager em = EngageManager.get();
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(
                    getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            em.setClientId(bundle.getString(CLIENT_ID_META));
            em.setClientSecret(bundle.getString(CLIENT_SECRET_META));
            em.setRefreshToken(bundle.getString(REFRESH_TOKEN_META));
            em.setHost(bundle.getString(HOST));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to load EngageSDK credential specific meta data. " +
                    "Did you provide your engage credentials in your manifest?");
        }

        EngageConfigManager cm = EngageConfigManager.get(getApplicationContext());
        em.setEngageConfigManager(cm);
        SharedPreferences sharedPreferences = getApplicationContext().
                getSharedPreferences(EngageConfig.ENGAGE_CONFIG_PREF_ID, Context.MODE_PRIVATE);
        em.setSharedPreferences(sharedPreferences);
        em.setContext(getApplicationContext());
        em.setUp();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        EngageManager.get().onApplicationTerminate();
    }
}

