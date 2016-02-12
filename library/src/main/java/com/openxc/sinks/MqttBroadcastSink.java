package com.openxc.sinks;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.util.concurrent.RateLimiter;
import com.openxc.VehicleManager;
import com.openxc.messages.SimpleVehicleMessage;
import com.openxc.messages.VehicleMessage;
import com.openxcplatform.R;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by mlgpmacl on 1/27/2016.
 */
public class MqttBroadcastSink extends ContextualVehicleDataSink {
    private BroadcasterThread mBroadcaster = new BroadcasterThread();
    private Context context;
    private String vehicleData = "";
    private ConcurrentHashMap<String, String> currentVehicleStatus;
    private Lock changeDetectLock = new ReentrantLock();
    private Condition valueChanged = changeDetectLock.newCondition();
    private final String TAG = "MQTTBroadcastSink";

    public MqttBroadcastSink(Context context, String dongleId,
                             String make, String model, String year) {
        super(context);
        this.context = context;
        Log.d(TAG, "adding sink with dong id = " + dongleId);
        currentVehicleStatus = new ConcurrentHashMap<>();
        currentVehicleStatus.put("dongle_id", "\"" + dongleId + "\"");
        currentVehicleStatus.put("vehicle_make", "\"" + make + "\"");
        currentVehicleStatus.put("vehicle_model", "\"" + model + "\"");
        currentVehicleStatus.put("vehicle_year", year);

        LocalBroadcastManager.getInstance(context).registerReceiver(dongReceiver,
                new IntentFilter("set-dongle-id"));
    }

    private BroadcastReceiver dongReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getExtras().containsKey("dongleId")) {
                String dongleId = intent.getExtras().getString("dongleId");
                Log.d("receiver", "Dong broadcast received.  New id = " + dongleId);
                currentVehicleStatus.put("dongle_id", "\"" + dongleId + "\"");
            }
        }
    };

    @Override
    public void stop() {
        mBroadcaster.done();
    }

    @Override
    public void receive(VehicleMessage message) {
        extractData(message);
        //The rest is handled by BroadcasterThread
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected void extractData(VehicleMessage message) {
        //sketchy cast so we can extract fields
        SimpleVehicleMessage sv = (SimpleVehicleMessage) message;
        String key = sv.getName();

        Class<?> newValueClass = sv.getValue().getClass();
        String newValue = sv.getValue().toString();
        if (newValueClass.equals(String.class)) {
            //wrap with quotes for JSON parser
            newValue = "\"" + newValue + "\"";
        }

        String result = currentVehicleStatus.put(key, newValue);
        if (!Objects.equals(result, newValue)) {
            //The value either changed or wasn't tracked previously
            //Send the updated vehicle status set
            try {
                changeDetectLock.lock();
                valueChanged.signal();
            } finally {
                changeDetectLock.unlock();
            }
        }
    }

    private void createJsonString() {
        try {
            changeDetectLock.lock();
            //wait here until we get the signal that data has changed, or we timeout
            valueChanged.await(30, TimeUnit.SECONDS);

            String msg = "{\"d\":{";
            for (Map.Entry<String, String> kv: currentVehicleStatus.entrySet()) {
                msg += "\"" + kv.getKey() + "\": " + kv.getValue() + ", ";
            }
            //remove trailing comma
            msg = msg.substring(0, msg.length() - 2);
            msg += "}}";
            vehicleData = msg;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            changeDetectLock.unlock();
        }
    }

    private class BroadcasterThread extends Thread {
        private boolean mRunning = true;

        public BroadcasterThread() {
            start();
        }

        public void done() {
            mRunning = false;
        }

        final RateLimiter rateLimiter = RateLimiter.create(10.0);

        @Override
        public void run() {
            while (mRunning) {
                //this call will block until vehicle data changes and then set "vehicleData"
                createJsonString();
                //only allow 10 broadcasts per second
                rateLimiter.acquire();

                Intent intent = new Intent();
                intent.setAction("com.pkg.perform.Ruby");
                intent.putExtra("VehicleData", vehicleData);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }
}
