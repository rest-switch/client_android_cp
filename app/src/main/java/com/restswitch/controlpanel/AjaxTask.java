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

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


////////////////////////////////////////
public class AjaxTask extends AsyncTask<Void, Void, AjaxTask.Data> {
    public static class Data {
        public Object param1 = null;
        public Object param2 = null;
        public int resultStatus = 0;
    }

    public interface AjaxEventHandler {
        void ajaxTaskComplete(AjaxTask.Data data);
    }

    private static final String trace_topic = "RestSwitch";
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 1500;

    // method: PUT
    // uri:    /pub/ah3avupwn
    // body:   [pulseRelay,2,250]
    // auth1:  ajxugyenm
    // auth2:  biwBxCrFhhMMBRlGCQ4ZJSZKDT2DPJCj7kHW4EWdSCM
    public String protocol = null;
    public String host = null;
    public String uri = null;
    public String method = null;
    public String body = null;
    public Properties headers = null;
    public AjaxTask.Data data = null;
    public AjaxEventHandler ajaxEventHandler = null;


    ////////////////////////////////////////
    public void putAjaxEventHandler(AjaxEventHandler ajaxEventHandler) {
        this.ajaxEventHandler = ajaxEventHandler;
    }


    ////////////////////////////////////////
    public void invoke(String protocol, String host, String uri, String method, Properties headers, String body, AjaxTask.Data data) {
        this.protocol = protocol;
        this.host = host;
        this.uri = uri;
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.data = data;
        this.execute();
    }


    ////////////////////////////////////////
    public boolean putRootCaCert(String caCert, boolean allowInvalidHost) {
        try {
            if(allowInvalidHost) {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(caCert.replaceAll("-----BEGIN CERTIFICATE-----", "").replaceAll("-----END CERTIFICATE-----", ""), Base64.DEFAULT)));
            String alias = cert.getSubjectX500Principal().getName();
            Log.v(trace_topic, alias);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);
            keyStore.setCertificateEntry(alias, cert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keyStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            return(true);
        } catch(Exception ex) {
            Log.e(trace_topic, ex.toString());
            return(false);
        }
    }


    ////////////////////////////////////////
    @Override
    protected AjaxTask.Data doInBackground(Void... arg0) {
        try {
            URL url = new URL(protocol + "://" + host + uri);
Log.e(trace_topic, "**********************" + url.toExternalForm());
            HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            httpConn.setReadTimeout(READ_TIMEOUT_MS);
            httpConn.setRequestMethod(method);
            httpConn.setRequestProperty("content-type", "application/json; charset=utf-8");
            httpConn.setRequestProperty("accept", "application/json");
            httpConn.setRequestProperty("accept-charset", CHARSET_UTF8);
            httpConn.setRequestProperty("connection", "keep-alive");
            //httpConn.setRequestProperty("connection", "close");
            Enumeration e = headers.propertyNames();
            while(e.hasMoreElements()) {
                String key = (String)e.nextElement();
                String val = headers.getProperty(key);
                httpConn.setRequestProperty(key, val);
            }

            OutputStream outs = httpConn.getOutputStream();
            outs.write(body.getBytes(CHARSET_UTF8));
            outs.close();

            int status = httpConn.getResponseCode();

            Log.v(trace_topic, "Received " + status + " from server.");
            if(status == 200) {
                final String resJson = convertStreamToString(httpConn.getInputStream());
                Log.v(trace_topic, "result: " + resJson);
            }

            httpConn.disconnect();

            if(this.data == null) this.data = new AjaxTask.Data();
            this.data.resultStatus = status;
            return(data);
        } catch(Exception ex) {
            Log.e(trace_topic, ex.toString());
            if(this.data == null) this.data = new AjaxTask.Data();
            this.data.resultStatus = 500;
            return(data);
        }
    }


    ////////////////////////////////////////
//    @Override
//    protected void onProgressUpdate(Integer... values) {
//        Log.v(trace_topic, "onProgressUpdate");
//        super.onProgressUpdate(values);
//    }


    ////////////////////////////////////////
    @Override
    protected void onPostExecute(AjaxTask.Data data) {
        super.onPostExecute(data);

        Log.i(trace_topic, "The server returned http status: " + ((data == null) ? 0 : data.resultStatus));

        if(ajaxEventHandler != null) {
            ajaxEventHandler.ajaxTaskComplete(data);
        }
    }


    ////////////////////////////////////////
    private static String convertStreamToString(java.io.InputStream stream) {
        java.util.Scanner s = new java.util.Scanner(stream, CHARSET_UTF8).useDelimiter("\\A");
        return(s.hasNext() ? s.next() : "");
    }

}
