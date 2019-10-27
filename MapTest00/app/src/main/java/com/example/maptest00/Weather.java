package com.example.maptest00;

import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;

public class Weather {
    // dependency needed:   compile 'com.android.support.appcompat-v7:23.4.0    '
//                      compile 'com.android.volley:volley:1.0.0'

//        string apiID = "9fc0107af02731f72a46618d46b873bb";

    public static void getWeather(RequestQueue queue, double lat, double lon, final MainActivity activity) {

        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" +
                lat + "&lon=" + lon + "&appid=9fc0107af02731f72a46618d46b873bb";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {

                    JSONObject mainObj = response.getJSONObject("main");
                    JSONArray arr = response.getJSONArray("weather");
                    JSONObject objID = arr.getJSONObject(0);
                    String tmp = String.valueOf(mainObj.getDouble("temp"));
                    String desc = objID.getString("description");
                    String loc = response.getString("name");

                    MainActivity.weather = tmp;
                    MainActivity.location = loc;
                    MainActivity.description = desc;
                    Log.e("TMP", tmp);
                    Log.e("LOC", loc);
                    Log.e("DESC", desc);

                    Calendar cald = Calendar.getInstance();
                    SimpleDateFormat simpleDateForm = new SimpleDateFormat("EEEE=MM-DD");
                    String dateFormat = simpleDateForm.format(cald.getTime());

                    activity.displayWeatherPopup();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError err) {

            }
        }
        );

        queue.add(request);
    }
}
