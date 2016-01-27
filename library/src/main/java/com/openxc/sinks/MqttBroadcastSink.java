package com.openxc.sinks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.openxc.messages.SimpleVehicleMessage;
import com.openxc.messages.VehicleMessage;

import java.util.ArrayList;

/**
 * Created by mlgpmacl on 1/27/2016.
 */
public class MqttBroadcastSink extends ContextualVehicleDataSink {
    private BroadcasterThread mBroadcaster = new BroadcasterThread();
    private Context context;
    private String vehicleData = "";

    public MqttBroadcastSink(Context context) {
        super(context);
        this.context = context;
    }
    @Override
    public void stop() {
        mBroadcaster.done();
    }

    @Override
    public void receive(VehicleMessage message) {
        this.vehicleData = this.extractData(message);
        mBroadcaster.run();
    }

    protected String extractData(VehicleMessage message) {
        String msg = "{\"d\":{";
        SimpleVehicleMessage sv = (SimpleVehicleMessage) message;
        msg += "\"" + sv.getName() + "\": " + sv.getValue().toString() + ", ";
        msg += "}}";
        return msg;
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
            Intent intent = new Intent();
            intent.setAction("com.pkg.perform.Ruby");
            intent.putExtra("VehicleData", MqttBroadcastSink.this.vehicleData);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }
}
