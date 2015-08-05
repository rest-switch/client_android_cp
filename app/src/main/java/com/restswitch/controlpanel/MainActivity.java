//
// Copyright 2015 The REST Switch Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, Licensor provides the Work (and each Contributor provides its 
// Contributions) on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied, including, 
// without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR 
// PURPOSE. You are solely responsible for determining the appropriateness of using or redistributing the Work and assume any 
// risks associated with Your exercise of permissions under this License.
//
// Author: John Clark (johnc@restswitch.com)
//

package com.restswitch.controlpanel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Properties;

import javax.crypto.Mac;

import org.json.JSONArray;


public class MainActivity extends ActionBarActivity implements AjaxTask.AjaxEventHandler {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id) {
            case R.id.action_about:
                AboutDialog about = new AboutDialog(this);
                about.setTitle("about this app");
                about.show();
                return true;
            case R.id.action_settings:
                this.startActivity(new android.content.Intent(this, SettingsActivity.class));
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    public void onButtonClick(View v) {
        try {
            ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if((networkInfo == null) || !networkInfo.isConnected()) {
                alertError("No network connection");
                return;
            }

            // get the device and io num
            int devnum = 0;
            int ionum = 0;
            try {
                String tag = (String)v.getTag();
                int val = Integer.parseInt(tag, 16);
                devnum = ((val >>> 4) & 0x0f);
                ionum = (val & 0x0f);
            } catch (Exception ex) {
                return;
            }
            //alertInfo("device num: " + devnum + "  io num: " + ionum);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pwdHash = prefs.getString("passwdHash", "");
            String devid = prefs.getString("devid" + devnum, "");
            String host = prefs.getString("host_name", "");

            String msg = ("[\"pulseRelay\"," + ionum + ",250]");
            sendDevice(devid, host, msg, pwdHash);
        } catch(Exception ex) {
            alertError(ex.getMessage());
        }
    }

    private void sendDevice(String devid, String host, String msg, String pwdHash) {
        try {
            final long utcStart = System.currentTimeMillis();
            String b32UntilUtc = B32Coder.encodeDatetimeNow(8000);  // valid for 8 sec
            String method = "PUT";
            String uri = ("/pub/" + devid);
            String val = (method + uri + msg + b32UntilUtc);

            String b64Hash = null;
            try {
                Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                hmacSha256.init(new javax.crypto.spec.SecretKeySpec(pwdHash.getBytes("utf-8"), "HmacSHA256"));
                byte[] hash = hmacSha256.doFinal(val.getBytes("UTF-8"));
                b64Hash = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            } catch(Exception ex) {
                alertError("Invalid password, verify app settings.");
                return;
            }

            Properties headers = new Properties();
            headers.setProperty("x-body", msg);
            headers.setProperty("x-auth1", b32UntilUtc);
            headers.setProperty("x-auth2", b64Hash);

            AjaxTask ajaxTask = new AjaxTask();
            ajaxTask.putAjaxEventHandler(this);
            boolean rc = ajaxTask.putRootCaCert(rootCa);
            if(!rc) {
                alertError("Failed to initialize network task.");
                return;
            }
            AjaxTask.Data data = new AjaxTask.Data();
            data.param1 = devid;
            data.param2 = utcStart;
            ajaxTask.invoke(method, host, uri, headers, msg, data);
        } catch(Exception ex) {
            alertError(ex.getMessage());
        }
    }

    public void ajaxTaskComplete(AjaxTask.Data data) {
        String devid = ((data.param1 instanceof String) ? (String)data.param1 : null);
        if((devid == null) || (devid.length() < 6)) {
            return; // nothing we can graph
        }

        long utcStart = ((data.param2 instanceof Long) ? (long)data.param2 : 0);
        int elapsed = ((utcStart == 0) ? 0 : (int)(System.currentTimeMillis() - utcStart));

        graphTimes(devid, elapsed);
    }

    private class TimingStats {
        public final int lastms;
        public final int minms;
        public final int avgms;
        public final int maxms;
        public TimingStats(final int lastms, final int minms, final int avgms, final int maxms) {
            this.lastms = lastms;
            this.minms = minms;
            this.avgms = avgms;
            this.maxms = maxms;
        }
    }

    private TimingStats getTimingStats(String devid, int ms, int valcnt) {
        if(valcnt < 5) valcnt = 5;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //                   devid1_times
        String key = (devid + "_times");
        String jsonData = prefs.getString(key, "");
        JSONArray data = null;
        try {
            data = new JSONArray(jsonData);
        } catch (Exception ex) {
            data = new JSONArray();
        }
        data.put(ms);

        // examine the last 'valcnt' values
        int minms = 8000;
        int maxms = 0;
        int totalms = 0;
        int len = 0;
        ArrayDeque<Integer> newData = new ArrayDeque<>();
        for(int i=data.length()-1; i>=0; --i) {
            int val = 0;
            try {
                val = data.getInt(i);
            } catch (Exception ex) {
                continue;
            }

            if(val > 7) { // 8ms sanity
                if(val > maxms) maxms = val;
                if(val < minms) minms = val;
                totalms += val;
                newData.addFirst(val);
                ++len;
                if(len >= valcnt) break;
            }
        }

        SharedPreferences.Editor prefsEdit = prefs.edit();
        try {
            JSONArray outJson = new JSONArray(newData);
            prefsEdit.putString(key, outJson.toString());
            //alertInfo(outJson.toString());
        } catch (Exception ex) {
            prefsEdit.remove(key);
        }
        prefsEdit.apply();

        int avgms = (len == 0) ? 0 : (totalms / len);

        TimingStats stats = new TimingStats(ms, minms, avgms, maxms);

        return(stats);
    }

    private void graphTimes(String devid, int ms) {
        TimingStats stats = getTimingStats(devid, ms, 10);

        // last : avg
        int totalms = (stats.avgms + stats.lastms);
        int lastbar = (stats.lastms * 100 / totalms);

        setProgressBars("last: " + stats.lastms + "ms", lastbar, "avg: "+stats.avgms+"ms", 100);
    }

    public void setProgressBars(String pb1Text, int pb1Pos, String pb2Text, int pb2Pos) {
        ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
        pb.setProgress(pb1Pos);
        pb.setSecondaryProgress(pb2Pos);

        TextView tv1 = (TextView)findViewById(R.id.progressBarText);
        tv1.setText(pb1Text);

        TextView tv2 = (TextView)findViewById(R.id.secondaryProgressBarText);
        tv2.setText(pb2Text);

        int pbwTot = pb.getWidth();
        int pbw1 = ((pbwTot * pb1Pos) / 100);
        tv1.setWidth(pbw1);

        pbw1 += 96;
        int pbw2 = (pbwTot - pbw1);
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)tv2.getLayoutParams();
        mlp.setMargins(pbw1, 2, 0, 0);
        tv2.setWidth(pbw2);
    }


    private void alertError(String msg) {
        new AlertDialog.Builder(this)
        .setTitle("Error")
        .setMessage(msg)
        .setNeutralButton("OK", null)
        .setIcon(android.R.drawable.ic_dialog_alert)
//        .setIconAttribute(android.R.attr.alertDialogIcon)
                .show();
    }

    private void alertInfo(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Information");
        builder.setMessage(msg);
        builder.setNeutralButton("OK", null);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.show();
    }

    // exit app listener
    public void onExitClick(View v) {
        this.finish();
    }

    // trust our root ca
    private static final String rootCa =
        "-----BEGIN CERTIFICATE-----" +
        "MIIGejCCBGKgAwIBAgIJAO52mPzbSfbhMA0GCSqGSIb3DQEBCwUAMIGEMQswCQYD" +
        "VQQGEwJVUzEUMBIGA1UEChMLUkVTVCBTd2l0Y2gxIjAgBgNVBAsTGVJFU1QgU3dp" +
        "dGNoIFRydXN0IE5ldHdvcmsxOzA5BgNVBAMTMlJFU1QgU3dpdGNoIENsYXNzIDMg" +
        "UHVibGljIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTE1MDgwNTE2MDg0OFoX" +
        "DTI1MDgwNTE2MDg0OFowgYQxCzAJBgNVBAYTAlVTMRQwEgYDVQQKEwtSRVNUIFN3" +
        "aXRjaDEiMCAGA1UECxMZUkVTVCBTd2l0Y2ggVHJ1c3QgTmV0d29yazE7MDkGA1UE" +
        "AxMyUkVTVCBTd2l0Y2ggQ2xhc3MgMyBQdWJsaWMgQ2VydGlmaWNhdGlvbiBBdXRo" +
        "b3JpdHkwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDMIPo9d81W474Z" +
        "vSS4uPOi+6lXcMWZ3GFX9hEMBBDgpIJ7UPG+avAUFQZHSZhI07Vn0HVhylhQa6js" +
        "UR7010DT4lSP9kau5iGPmWPoEgdH9BDeb743uZONZQzvnLMhXr3toFKUxqbhz3NA" +
        "bA+RXhbQcB2aJ1ek1l5+W7QUa1hxYuZgtPlamdvBTHnjyeOslyN22Q4mYPDynzKL" +
        "kDFKNnBbdqG/GxsK85mASTQ2PCQ9OFhWSaQ+TYx2UzQkzkC1ToXiLRG4nC1h+7lj" +
        "DuPDuX2bQa83Ix8/RXHsALppNphtzOuYBoF/UfsJXYuKTDiyc5J4/i17ZHccy1T0" +
        "dfJutEfq74yRpjel/pyR2PTIgk7282c6Sb04HetcEbVZTk8pRnYTsj3jtm2V9Jls" +
        "ztEnASh9O3dMegNxKCS2DGse4XfrDtQ6nrG2h7+0Qpvov3EvDOK8i/FJOCA4PpNI" +
        "myZ+OR+qJhVCQM974ubY3ui8r7j+Erbkca5sS0uf1dy9+2OxhbuVr9vg2qrenZbQ" +
        "nGUPLHPG6xcAM2pwRUpVNKtrrU+bWVf5VWkj3f9JqhjmZdUiaJ/Oa1HWbqFpZn6Q" +
        "ZI4vz10d7k4fiefNLDtEoEb8kqEjLd+CJiWC2qe0ye1IhUG9xXg89Xx5itq3yeCO" +
        "SAq1/UoF+gyNDYKiVWQZNcrsAOdqLQIDAQABo4HsMIHpMB0GA1UdDgQWBBQQqGyH" +
        "Pq4RY1oooNeR48yW52wROTCBuQYDVR0jBIGxMIGugBQQqGyHPq4RY1oooNeR48yW" +
        "52wROaGBiqSBhzCBhDELMAkGA1UEBhMCVVMxFDASBgNVBAoTC1JFU1QgU3dpdGNo" +
        "MSIwIAYDVQQLExlSRVNUIFN3aXRjaCBUcnVzdCBOZXR3b3JrMTswOQYDVQQDEzJS" +
        "RVNUIFN3aXRjaCBDbGFzcyAzIFB1YmxpYyBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0" +
        "eYIJAO52mPzbSfbhMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBABrI" +
        "oiP8ivzw1FMmqBvKfze6KBTzDd2CB5g9aany12Cdo736QqMBHM4Be+ozSuSyL6Cc" +
        "l13aLzqCqJTMtn7Ug59N4uf8BJu6rrcyaV79d4MpUHon2WGyGSeYvBIGkxpQgSFE" +
        "IneKlr4REnG3Hu2f9q3o148LXTzpeQ9WaVQWTO99Ke0WiKk0cdH1i3LTTOl13eL5" +
        "mSMfbgXnYqfYjDPSy/lithL90zxemMPmj/lsOwMf/cToMyVqBDS/8DoSnt2zoUiZ" +
        "GhejCZT9ERPzO2cSB0BuqzhL5LILiINzuQFrSwWmiq0ptCYO30+ugunWQKUM/1YZ" +
        "hfLWo8DrVXNN0Wf91C9LL75kvl1reJCglSkE485HeeeFAC7T7exiPt8zHWhZiLQB" +
        "E5IeMKNWAfM4gsvCk7c9tVCK8hBjoPiuaCgSYNu33sjTpE9jtAaSwwVj36eFGn3G" +
        "W84RlBsSJC6lZqOPzDaUkphOX3/GVphE8MW7ipriy0sO3222G9xwaQfjXAp0QELc" +
        "Cz8gn4vVY8kr45CIOEKYQgaflNg88VAjKpe5VoytwKvySq9z01ZTAp58I6UVpt79" +
        "I+nddOOyfknArncc8KxddRk5GLnzB7pB8A6I2AEIGQ0p45rhgvJtvnn27J3fSPE0" +
        "wbY4maGJQNfCWIwdDTJ/Zn3zRqgpd7dUdiwG1bX0" +
        "-----END CERTIFICATE-----";
}
