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


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;

import android.util.Base64;
import android.util.Log;

import javax.crypto.Mac;


public class SettingsActivity extends ActionBarActivity {
    private static final String trace_topic = "HomeAutomation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(trace_topic, "SettingsActivity::onCreate");
        super.onCreate(savedInstanceState);
        this.getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
    }


    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            Log.d(trace_topic, "PrefsFragment::onCreate -> " + savedInstanceState);
            super.onCreate(savedInstanceState);
            this.addPreferencesFromResource(R.xml.preferences);

            // attach change listeners
            Preference.OnPreferenceChangeListener stringPrefChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    preference.setSummary("  " + value.toString());
                    return(true);
                }
            };

            // load prefs
            SharedPreferences sp = this.getPreferenceManager().getSharedPreferences();

            // device id 1
            Preference devid1 = this.findPreference("devid1");
            devid1.setSummary("  " + sp.getString(devid1.getKey(), ""));
            devid1.setOnPreferenceChangeListener(stringPrefChangeListener);

            // device id 2
            Preference devid2 = this.findPreference("devid2");
            devid2.setSummary("  " + sp.getString(devid2.getKey(), ""));
            devid2.setOnPreferenceChangeListener(stringPrefChangeListener);

            // host name
            Preference hostName = this.findPreference("host_name");
            hostName.setSummary("  " + sp.getString(hostName.getKey(), ""));
            hostName.setOnPreferenceChangeListener(stringPrefChangeListener);

            // email
            Preference email = this.findPreference("email");
            email.setSummary("  " + sp.getString(email.getKey(), ""));
            email.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                preference.setSummary("  " + value.toString());

                // update password field
                Preference passwd = PrefsFragment.this.getPreferenceManager().findPreference("passwd");
                passwd.setSummary("Email address has changed, the password must be updated.");
                passwd.setIcon(android.R.drawable.ic_delete);
                return(true);
                }
            });

            // password
            Preference passwd = this.findPreference("passwd");
            //passwd.setSummary("  " + sp.getString(passwd.getKey(), ""));
            passwd.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                try {
                    //java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
                    //sha256.update(value.toString().getBytes());
                    //byte[] hash = sha256.digest();

                    PreferenceManager pm = PrefsFragment.this.getPreferenceManager();
                    SharedPreferences sp = pm.getSharedPreferences();
                    String val = sp.getString("email", "");
                    String key = value.toString();

                    Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                    hmacSha256.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("utf-8"), "HmacSHA256"));
                    byte[] hash = hmacSha256.doFinal(val.getBytes("utf-8"));
                    String b64Hash = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                    SharedPreferences.Editor editor = preference.getEditor();
                    editor.putString("passwd", "");
                    editor.putString("passwdHash", b64Hash);
                    editor.commit();

                    // put the password field
                    Preference passwd = pm.findPreference("passwd");
                    PrefsFragment.this.getPreferenceScreen().removePreference(passwd);
                    PrefsFragment.this.getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
                } catch(Exception ex) { }
                return(false);
                }
            });
        }
    }
}
