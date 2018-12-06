package de.xorg.gsapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class Feriencounter {

    Activity mActivity;
    Handler handler;
    FeriencounterCallback fc;

    static class FeriencounterCallback implements Runnable {
        String daysUntil;
        String nameFerien;

        public void setDays(String _days) {
            this.daysUntil = _days;
        }
        public void setName(String _name) { this.nameFerien = _name; }

        public void run() {
            Log.d("Feriencounter", daysUntil + " Tage bis/noch " + nameFerien);
        }
    }

    Feriencounter(Activity a, FeriencounterCallback callback) {
        this.mActivity = a;
        this.fc = callback;
    }


    public void requestFerien() {
        Handler handler = new Handler();

        final Runnable r = new Runnable() {
            public void run() {
                File ferien = new File(mActivity.getCacheDir(), "ferien.json");
                long age = new Date().getTime() - PreferenceManager.getDefaultSharedPreferences(mActivity).getLong(Util.Preferences.FERIEN_FETCHED, 0);

                if(!ferien.exists() || TimeUnit.MILLISECONDS.toDays(age) > 14) {
                    fetch();
                } else {
                    try {
                        parseFerien(new String(Files.toByteArray(ferien), Charset.forName("UTF-8")));
                    } catch(IOException e) {
                        e.printStackTrace();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }


            }
        };

        handler.postDelayed(r, 1000);
    }

    public String capitalize(String inp) {
        return inp.substring(0, 1).toUpperCase() + inp.substring(1).toLowerCase();
    }

    public void parseFerien(String jsonInp) throws JSONException, ParseException {
        JSONArray root = new JSONArray(jsonInp);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        JSONObject nearest = null;
        Date now = new Date();
        Date nearestDate = null;

        for(int i = 0; i < root.length(); i++) {
            JSONObject disFerien = root.getJSONObject(i);
            Date begin = df.parse(disFerien.getString("start"));
            Date end = df.parse(disFerien.getString("end"));
            if (!end.before(now)) { //Ferien enden in der Vergangenheit -> Ignorieren
                if (now.after(begin) && now.before(end)) { //Heute ist nach Ferienbeginn und vor Ferienende -> In den Ferien
                    long timeRem = TimeUnit.MILLISECONDS.toDays(end.getTime() - now.getTime());
                    String timeSuff;
                    if (timeRem > 7) {
                        timeRem = timeRem / 7;
                        timeSuff = (timeRem == 1 ? "Woche" : "Wochen");
                    } else {
                        timeSuff = (timeRem == 1 ? "Tag" : "Tage");
                    }
                    announceDays(String.format("Noch %d %s", timeRem, timeSuff), capitalize(disFerien.getString("name")));
                    return; //Funktion fertig, da "passende" Ferien definitiv gefunden
                } else {
                    if (nearest == null) { //Setze das "Referenzdatum" auf das erste zukünftige Datum
                        nearest = root.getJSONObject(i);
                        nearestDate = df.parse(nearest.getString("start"));
                        continue;
                    }

                    if ( (begin.getTime() - now.getTime()) < (nearestDate.getTime() - now.getTime())) { //Diese Ferien früher als Gemerkte
                        nearest = disFerien;
                        nearestDate = begin;
                    }
                }
            }
        }

        long timeRem = TimeUnit.MILLISECONDS.toDays(nearestDate.getTime() - now.getTime());
        String timeSuff;
        if (timeRem > 7) {
            timeRem = timeRem / 7;
            timeSuff = (timeRem == 1 ? "Woche" : "Wochen");
        } else if(timeRem < 0) {
            Timber.w("Not displaying next holidays as remaining days is less than 0!");
            return;
        } else {
            timeSuff = (timeRem == 1 ? "Tag" : "Tage");
        }
        announceDays(String.format("In %d %s", timeRem, timeSuff), capitalize(nearest.getString("name")));
    }

    public void fetch() {
        OkHttpClient.Builder b = new OkHttpClient.Builder();
        b.readTimeout(20, TimeUnit.SECONDS);
        b.connectTimeout(20, TimeUnit.SECONDS);

        OkHttpClient client = b.build();

        System.setProperty("http.keepAlive", "false");

        Request request = new Request.Builder()
                .url("https://ferien-api.de/api/v1/holidays/TH/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if(!response.isSuccessful()) {
                    Timber.e("onResponse FAILED (" + response.code() + ")");
                    return;
                }

                String result = response.body().string();

                Files.write(result.getBytes(Charset.forName("UTF-8")), new File(mActivity.getCacheDir(), "ferien.json")); //Ferien-JSON zwischenspeichern

                SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(mActivity).edit(); //Zeitpunkt des Ferien-JSON-Downloads speichern
                ed.putLong(Util.Preferences.FERIEN_FETCHED, new Date().getTime());
                ed.commit();

                try {
                    parseFerien(result);
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

        });
    }

    public void announceDays(String days, String name) {
        fc.setDays(days);
        fc.setName(name);
        mActivity.runOnUiThread(fc);
    }
}