package com.silverpop.engage;

import com.silverpop.engage.config.EngageConfig;
import com.silverpop.engage.config.EngageConfigManager;
import com.silverpop.engage.deeplinking.EngageDeepLinkManager;
import com.silverpop.engage.domain.UBF;
import com.silverpop.engage.domain.XMLAPI;
import com.silverpop.engage.location.manager.EngageLocationManager;
import org.mobiledeeplinking.android.Handler;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;
import com.silverpop.engage.location.manager.plugin.EngageLocationManagerDefault;
import com.silverpop.engage.util.EngageExpirationParser;
import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;

/**
 * Created by azuercher on 2/11/15.
 */
public class EngageManager {
    private static final String TAG = EngageManager.class.getName();
    private String clientId = null;
    private String clientSecret = null;
    private String refreshToken = null;
    private String host = null;

    private final String APP_INSTALLED = "APP_INSTALLED";
    private final String SESSION = "SESSION";

    private Date sessionExpires = null;
    private Date sessionBegan = null;

    private EngageLocationManager locationManager;
    private EngageConfigManager engageConfigManager;
    private SharedPreferences sharedPreferences;
    private Context context;

    private static EngageManager instance;
    public static EngageManager get() {
        Log.e(TAG, "Version 1.0.0-digicel 201502110900");
        if (instance == null) {
            instance = new EngageManager();
        }
        return instance;
    }

    public void onApplicationTerminate(){
        if (isSessionExpired()) {
            createAndPostSessionEndedEvent();
        }
    }
    public void setUp() {
        setupPlugins();
        //Initializes the UBFManager and XMLAPIManager instances.
        XMLAPIManager.initialize(context, clientId, clientSecret, refreshToken, host);
        UBFManager.initialize(context, clientId, clientSecret, refreshToken, host);
        setupDeepLinkManager();
        setupUbfs();
    }
    private void setupDeepLinkManager() {
        //Registers a default deep linking handler for parsing URL parameters
        EngageDeepLinkManager.registerHandler(EngageDeepLinkManager.DEFAULT_HANDLER_NAME, new Handler() {
            @Override
            public Map<String, String> execute(Map<String, String> paramsMap) {
                UBFManager.get().handleExternalURLOpenedEvents(context, paramsMap);
                return paramsMap;
            }
        });
    }
    private void setupUbfs() {
        //Check if this is the first time the app has been ran or not
        final boolean firstInstall = sharedPreferences.getString(APP_INSTALLED, null) == null;
        if (firstInstall) {
            Log.d(TAG, "EngageSDK - Application has been installed/ran for the first time");
            sharedPreferences.edit().putString(APP_INSTALLED, "YES").commit();

            waitForPrimaryUserIdThenCreateInstalledEvent();

            //Create the Last known user location database columns
            Map<String, Object> bodyElements = new HashMap<String, Object>();
            bodyElements.put("LIST_ID", engageConfigManager.engageListId());
            bodyElements.put("COLUMN_NAME", engageConfigManager.lastKnownLocationColumn());
            bodyElements.put("COLUMN_TYPE", 0);
            bodyElements.put("DEFAULT", "");
            XMLAPI createLastKnownLocationColumns = new XMLAPI("AddListColumn", bodyElements);
            XMLAPIManager.get().postXMLAPI(createLastKnownLocationColumns, null, null);

            bodyElements = new HashMap<String, Object>();
            bodyElements.put("LIST_ID", engageConfigManager.engageListId());
            bodyElements.put("COLUMN_NAME", engageConfigManager.lastKnownLocationTimestampColumn());
            bodyElements.put("COLUMN_TYPE", 3);
            bodyElements.put("DEFAULT", "");
            createLastKnownLocationColumns = new XMLAPI("AddListColumn", bodyElements);
            XMLAPIManager.get().postXMLAPI(createLastKnownLocationColumns, null, null);
        }

        //Examine the session and determine if events should be posted.
        handleSessionApplicationLaunch(firstInstall);
    }
    private void setupPlugins() {
        if (engageConfigManager.locationServicesEnabled()) {
            String pluggableLocationClassname = engageConfigManager.pluggableLocationManagerClassName();
            if (pluggableLocationClassname != null) {
                try {
                    Class<?> clazz = Class.forName(pluggableLocationClassname);
                    locationManager = (EngageLocationManager) clazz.newInstance();
                    locationManager.setContext(context);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            if (locationManager == null) {
                Log.w(TAG, "Unable to create PluggableLocationManager instance. Defaulting to : '"
                        + EngageLocationManagerDefault.class.getName() + "'");
                //Create the default EngageLocationManager instance.
                locationManager = new EngageLocationManagerDefault();
                locationManager.setContext(context);
            }

            locationManager.startLocationUpdates();
            Log.d(TAG, "Starting location services");

        } else {
            Log.d(TAG, "Location services are disabled");
        }
    }
    private void handleSessionApplicationLaunch(final boolean firstInstall) {
        if (sessionBegan == null) {
            //Checks to see if a previous session has been persisted.
            long sessionStartedTimestamp = sharedPreferences.getLong(SESSION, -1);
            if (sessionStartedTimestamp == -1) {
                //Start a session.
                sessionBegan = new Date();
                // app just installed so wait for login
                if (firstInstall) {
                    waitForPrimaryUserIdThenCreateSessionStartedEvent();
                } else {
                    createAndPostSessionStartedEvent();
                }
                sharedPreferences.edit().putLong(SESSION, sessionBegan.getTime()).commit();
            } else {
                sessionBegan = new Date(sessionStartedTimestamp);
            }

            EngageExpirationParser parser = new EngageExpirationParser(engageConfigManager.sessionLifecycleExpiration(), sessionBegan);
            sessionExpires = parser.expirationDate();

            if (isSessionExpired()) {
                createAndPostSessionEndedEvent();
                sharedPreferences.edit().putLong(SESSION, -1).commit();
                createAndPostSessionStartedEvent();
            }

        } else {
            //Compare the current time to the session began time.
            if (isSessionExpired()) {
                createAndPostSessionEndedEvent();
                sharedPreferences.edit().putLong(SESSION, -1).commit();
                createAndPostSessionStartedEvent();
            }
        }
    }
    private void createAndPostSessionEndedEvent() {
        UBFManager.get().postEvent(UBF.sessionEnded(context, null));
    }
    private void createAndPostSessionStartedEvent() {
        UBFManager.get().postEvent(UBF.sessionStarted(context, null, EngageConfig.currentCampaign(context)));
    }
    private void waitForPrimaryUserIdThenCreateInstalledEvent() {
        if (EngageConfig.primaryUserId(context) != null && !EngageConfig.primaryUserId(context).isEmpty()) {
            Log.e(TAG, "installedEvent primaryUserId set, posting");
            createAndPostInstalledEvent();
        } else if (EngageConfig.anonymousUserId(context) != null && !EngageConfig.anonymousUserId(context).isEmpty()) {
            Log.e(TAG, "installedEvent anonymousUserId set, posting");
            createAndPostInstalledEvent();
        } else {
            Log.e(TAG, "installedEvent registering listener for PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT");
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.e(TAG, "installedEvent PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT received");
                            createAndPostInstalledEvent();

                            // remove listener
                            EngageManager.this.context.unregisterReceiver(this);
                            Log.i(TAG, "Removed primary user id listener for installed event");
                        }
                    },
                    new IntentFilter(EngageConfig.PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT));
        }
    }
    private void createAndPostInstalledEvent() {
        UBF appInstalled = UBF.installed(context, null);
        UBFManager.get().postEvent(appInstalled);
    }
    private void waitForPrimaryUserIdThenCreateSessionStartedEvent() {
        if (EngageConfig.primaryUserId(context) != null && !EngageConfig.primaryUserId(context).isEmpty()) {
            Log.e(TAG, "sessionEvent primaryUserId set, posting");
            createAndPostSessionStartedEvent();
        } else if (EngageConfig.anonymousUserId(context) != null && !EngageConfig.anonymousUserId(context).isEmpty()) {
            Log.e(TAG, "sessionEvent anonymousUserId set, posting");
            createAndPostSessionStartedEvent();
        } else {
            Log.e(TAG, "sessionEvent registering listener for PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT");
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.e(TAG, "sessionEvent PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT received");
                            createAndPostSessionStartedEvent();

                            // remove listener
                            EngageManager.this.context.unregisterReceiver(this);
                            Log.i(TAG, "Removed primary user id listener for session started event");
                        }
                    },
                    new IntentFilter(EngageConfig.PRIMARY_USER_OR_ANONYMOUD_ID_SET_EVENT));
        }
    }
    private boolean isSessionExpired() {
        if (sessionExpires.compareTo(new Date()) < 0) {
            return true;
        } else {
            return false;
        }
    }
    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public EngageLocationManager getLocationManager() {
        return locationManager;
    }

    public void setLocationManager(EngageLocationManager locationManager) {
        this.locationManager = locationManager;
    }

    public EngageConfigManager getEngageConfigManager() {
        return engageConfigManager;
    }

    public void setEngageConfigManager(EngageConfigManager engageConfigManager) {
        this.engageConfigManager = engageConfigManager;
    }}