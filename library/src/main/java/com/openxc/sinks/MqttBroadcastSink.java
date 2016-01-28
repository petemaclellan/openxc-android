package com.openxc.sinks;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.openxc.messages.SimpleVehicleMessage;
import com.openxc.messages.VehicleMessage;

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

    public MqttBroadcastSink(Context context) {
        super(context);
        this.context = context;
        currentVehicleStatus = new ConcurrentHashMap<>();
    }

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

        @Override
        public void run() {
            while (mRunning) {
                //this call will block until vehicle data changes and then set "vehicleData"
                createJsonString();

                Intent intent = new Intent();
                intent.setAction("com.pkg.perform.Ruby");
                intent.putExtra("VehicleData", vehicleData);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }
}
