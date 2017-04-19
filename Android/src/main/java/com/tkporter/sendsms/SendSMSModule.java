package com.tkporter.sendsms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.os.Build;
import android.provider.Telephony;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONException;

import java.util.ArrayList;

public class SendSMSModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private Callback callback = null;
    private static final int REQUEST_CODE = 5235;
    private static final String INTENT_FILTER_SMS_SENT = "SMS_SENT";

    public SendSMSModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        // reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "SendSMS";
    }

    private boolean checkSupport() {
        Activity ctx = getCurrentActivity();
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private void sendMessage(final Promise promise, String phoneNumber, String message) {
        SmsManager manager = SmsManager.getDefault();
        final ArrayList<String> parts = manager.divideMessage(message);

        // by creating this broadcast receiver we can check whether or not the SMS was sent
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

            boolean anyError = false; //use to detect if one of the parts failed
            int partsCount = parts.size(); //number of parts to send

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case SmsManager.STATUS_ON_ICC_SENT:
                    case Activity.RESULT_OK:
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        anyError = true;
                        break;
                }
                // trigger the callback only when all the parts have been sent
                partsCount--;
                if (partsCount == 0) {
                    if (anyError) {
                        promise.reject(new Exception("error"));
                    } else {
                        promise.resolve(Arguments.createMap());
                    }
                    getCurrentActivity().unregisterReceiver(this);
                }
            }
        };

        // randomize the intent filter action to avoid using the same receiver
        String intentFilterAction = INTENT_FILTER_SMS_SENT;
        getCurrentActivity().registerReceiver(broadcastReceiver, new IntentFilter(intentFilterAction));

        PendingIntent sentIntent = PendingIntent.getBroadcast(getCurrentActivity(), 0, new Intent(intentFilterAction), 0);

        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        //     String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(reactContext);
        //     sendIntent = new Intent(Intent.ACTION_SEND);
        //     if (defaultSmsPackageName != null){
        //         sendIntent.setPackage(defaultSmsPackageName);
        //     }
        //     sendIntent.setType("text/plain");
        // }else {
        //     sendIntent = new Intent(Intent.ACTION_VIEW);
        //     sendIntent.setType("vnd.android-dir/mms-sms");
        // }
        //
        // sendIntent.putExtra("sms_body", body);

        // depending on the number of parts we send a text message or multi parts
        if (parts.size() > 1) {
            ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentIntent);
            }
            manager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null);
        }
        else {
            manager.sendTextMessage(phoneNumber, null, message, sentIntent, null);
        }
    }

    @ReactMethod
    public void send(final ReadableMap options, final Promise promise) {
        new Thread(new Runnable() {
            public void run() {
                //parsing arguments
                String separator = ";";
                if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                    // See http://stackoverflow.com/questions/18974898/send-sms-through-intent-to-multiple-phone-numbers/18975676#18975676
                    separator = ",";
                }
                String message = options.hasKey("body") ? options.getString("body") : "";
                String recipients = options.hasKey("recipients") ? options.getArray("recipients").toString() : null;

                if (!checkSupport()) {
                    promise.reject(new Exception("not supported"));
                    return;
                }
                sendMessage(promise, recipients, message);
                return;
            }
        }).start();
    }

}
