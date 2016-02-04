package com.openxc.enabler.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.openxc.VehicleManager;
import com.openxc.sinks.DataSinkException;
import com.openxc.sinks.MqttBroadcastSink;
import com.openxc.sinks.UploaderSink;
import com.openxc.sinks.VehicleDataSink;
import com.openxcplatform.enabler.R;

/**
 * Enable or disable uploading of a vehicle trace to a remote web server.
 *
 * The URL of the web server to upload the trace to is read from the shared
 * preferences.
 */
public class UploadingPreferenceManager extends VehiclePreferenceManager {
    private final static String TAG = "UploadingPreferenceManager";
    private VehicleDataSink mUploader;

    public UploadingPreferenceManager(Context context) {
        super(context);
    }

    public void close() {
        super.close();
        stopUploading();
    }

    protected PreferenceListener createPreferenceListener() {
        return new PreferenceListener() {
            private int[] WATCHED_PREFERENCE_KEY_IDS = {
                R.string.uploading_checkbox_key,
                R.string.uploading_path_key,
                R.string.vehicle_make_key,
                R.string.vehicle_model_key,
                R.string.vehicle_year_key
            };

            protected int[] getWatchedPreferenceKeyIds() {
                return WATCHED_PREFERENCE_KEY_IDS;
            }

            public void readStoredPreferences() {
                setUploadingStatus(getPreferences().getBoolean(getString(
                                R.string.uploading_checkbox_key), false));
            }
        };
    }

    @SuppressLint("LongLogTag")
    private void setUploadingStatus(boolean enabled) {
        Log.i(TAG, "Setting uploading to " + enabled);
        if(enabled) {
            if(mUploader != null) {
                stopUploading();
            }

            String make = getPreferenceString(R.string.vehicle_make_key);
            String model = getPreferenceString(R.string.vehicle_model_key);
            String year = getPreferenceString(R.string.vehicle_year_key);

            try {
                VehicleManager vehicleManager = getVehicleManager();
                mUploader = new MqttBroadcastSink(getContext(), vehicleManager, make, model, year);
            } catch(Exception e) {
                Log.w(TAG, "Unable to add uploader sink", e);
                return;
            }
            getVehicleManager().addSink(mUploader);
        } else {
            stopUploading();
        }
    }

    private void stopUploading() {
        if(getVehicleManager() != null){
            getVehicleManager().removeSink(mUploader);
            mUploader = null;
        }
    }
}