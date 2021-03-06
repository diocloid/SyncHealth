package com.example.amathur.synchealth;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataService;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class Samsung {

    //Samsung
    private HealthDataStore mStore;
    private HealthConnectionErrorResult mConnError;
    private final Set<HealthPermissionManager.PermissionKey> mKeySet;
    private HealthResultHolder<HealthDataResolver.ReadResult> result;
    private final WeakReference<Activity> mInstance;
    private final String APP_TAG = "SYNCHEALTH-SAMSUNG";

    Samsung(Activity mInstance){
        this.mInstance = new WeakReference<>(mInstance);
        mKeySet = new HashSet<>();
//        mKeySet.add(new HealthPermissionManager.PermissionKey("com.samsung.shealth.step_daily_trend", HealthPermissionManager.PermissionType.READ));
        mKeySet.add(new HealthPermissionManager.PermissionKey(HealthConstants.StepCount.HEALTH_DATA_TYPE, HealthPermissionManager.PermissionType.READ));
        mKeySet.add(new HealthPermissionManager.PermissionKey(HealthConstants.HeartRate.HEALTH_DATA_TYPE, HealthPermissionManager.PermissionType.READ));
        HealthDataService healthDataService = new HealthDataService();

        try {
            healthDataService.initialize(mInstance);
            mStore = new HealthDataStore(mInstance, mConnectionListener);
            // Request the connection to the health data store
            mStore.connectService();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult> mPermissionListener =
            result -> {
                Log.d(APP_TAG, "Permission callback is received.");
                Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = result.getResultMap();
                if (resultMap.containsValue(Boolean.FALSE)) {
                    Log.d(APP_TAG, "Permission Faliure");
                }
//                else {
//                    // Get the current step count and display it
//                }
            };

    private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {

        @Override
        public void onConnected() {
            Log.d(APP_TAG, "Health data service is connected.");
            HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);

            try {
                // Check whether the permissions that this application needs are acquired
                Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = pmsManager.isPermissionAcquired(mKeySet);

                if (resultMap.containsValue(Boolean.FALSE)) {
                    Log.d(APP_TAG, "Permission for Samsung health failed retrying");
                    // Request the permission for reading step counts if it is not acquired
                    pmsManager.requestPermissions(mKeySet, mInstance.get()).setResultListener(mPermissionListener);
                }
//                else {
//                    // Get the current step count and display it
////                    readSamsungData();
//                }
            } catch (Exception e) {
                Log.e(APP_TAG, e.getClass().getName() + " - " + e.getMessage());
                Log.e(APP_TAG, "Permission setting fails.");
            }
        }

        @Override
        public void onConnectionFailed(HealthConnectionErrorResult error) {
            Log.d(APP_TAG, "Health data service is not available.");
            showConnectionFailureDialog(error);
        }

        @Override
        public void onDisconnected() {
            Log.d(APP_TAG, "Health data service is disconnected.");
        }

        private void showConnectionFailureDialog(HealthConnectionErrorResult error) {

            AlertDialog.Builder alert = new AlertDialog.Builder(mInstance.get());
            mConnError = error;
            String message = "Connection with Samsung Health is not available";

            if (mConnError.hasResolution()) {
                switch(error.getErrorCode()) {
                    case HealthConnectionErrorResult.PLATFORM_NOT_INSTALLED:
                        message = "Please install Samsung Health";
                        break;
                    case HealthConnectionErrorResult.OLD_VERSION_PLATFORM:
                        message = "Please upgrade Samsung Health";
                        break;
                    case HealthConnectionErrorResult.PLATFORM_DISABLED:
                        message = "Please enable Samsung Health";
                        break;
                    case HealthConnectionErrorResult.USER_AGREEMENT_NEEDED:
                        message = "Please agree with Samsung Health policy";
                        break;
                    default:
                        message = "Please make Samsung Health available";
                        break;
                }
            }

            alert.setMessage(message);

            alert.setPositiveButton("OK", (dialog, id) -> {
                if (mConnError.hasResolution()) {
                    mConnError.resolve(mInstance.get());
                }
            });

            if (error.hasResolution()) {
                alert.setNegativeButton("Cancel", null);
            }

            alert.show();
        }
    };

    private HealthDataResolver.ReadRequest buildTodayStepCountReadRequest(long startTime) {

        HealthDataResolver.Filter filter = HealthDataResolver.Filter.greaterThan("start_time", startTime);

        return new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
                .setFilter(filter)
                .build();
    }

    private HealthDataResolver.ReadRequest buildTodayHearRateReadRequest(long startTime) {

        HealthDataResolver.Filter filter = HealthDataResolver.Filter.greaterThan("start_time", startTime);

        return new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
                .setFilter(filter)
                .build();
    }

    public List<HealthData> getAllStepsDataPoints(long startTime) throws InterruptedException {
        List<HealthData> dps = new ArrayList<>();
        HealthDataResolver.ReadRequest query = buildTodayStepCountReadRequest(startTime);
        HealthDataResolver dataResolver = new HealthDataResolver(mStore, LocalThreadPool.callbackHandler);
        AtomicBoolean done = new AtomicBoolean(false);
        try{
            result = dataResolver.read(query);
            result.setResultListener(result -> {
                for (HealthData data : result) {
                    if (data.getString("deviceuuid").equals("D1Umv4y7BL"))
                        dps.add(data);
                }
                done.set(true);
                synchronized (done) {
                    done.notifyAll();
                }
            });
        }
        catch (SecurityException e ) {
            Log.d(APP_TAG, e.getMessage());
        }
        synchronized (done) {
            while (!done.get()) {
                done.wait();
            }
        }
        return dps;
    }

    public List<HealthData> getAllHRDataPoints(long startTime) throws InterruptedException {
        List<HealthData> dps = new ArrayList<>();
HealthDataResolver.ReadRequest query = buildTodayHearRateReadRequest(startTime);
        HealthDataResolver dataResolver = new HealthDataResolver(mStore, LocalThreadPool.callbackHandler);
        AtomicBoolean done = new AtomicBoolean(false);
        try{
            result = dataResolver.read(query);
            result.setResultListener(result -> {
                for (HealthData data : result) {
                    dps.add(data);
                }
                done.set(true);
                synchronized (done) {
                    done.notifyAll();
                }
            });
        }
        catch (SecurityException e ) {
            Log.d(APP_TAG, e.getMessage());
        }
        synchronized (done) {
            while (!done.get()) {
                done.wait();
            }
        }
        return dps;
    }



    public void disconnect(){
        mStore.disconnectService();
    }
}