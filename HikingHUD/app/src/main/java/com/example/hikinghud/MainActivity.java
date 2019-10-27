package com.example.hikinghud;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Double.NaN;

public class MainActivity extends AppCompatActivity {

    public static ReentrantLock cacheLock = new ReentrantLock();
    public static AtomicBoolean isWaterCacheUpdated = new AtomicBoolean(false);
    public static TextView view;
    public static ArrayList<WaterSource> sources;

    public static double nearestUnNatX = NaN;
    public static double nearestUnNatY = NaN;
    public static double nearestNatX = NaN;
    public static double nearestNatY = NaN;

    public static String weather;
    public static String location;
    public static String description;

    public enum ReqType
    {
        WATER,
        WEATHER,
        BOUNDARY
    }

    public static void findClosestWater(double lat, double lon)
    {
        double magUnNat = Double.MAX_VALUE;
        double magNat = Double.MAX_VALUE;
        //linear search through water nodes for lowest dist
        for(WaterSource ws : sources)
        {
            if(ws.isNatural)
            {
                for(int i = 0; i < ws.nodesID.size(); i++)
                {
                    double nodeLat = ws.nodesX.get(i);
                    double nodeLon = ws.nodesY.get(i);
                    double mag = Math.sqrt(Math.pow(lat - nodeLat, 2) + Math.pow(lon - nodeLon, 2));

                    if(mag < magNat)
                    {
                        magNat = mag;
                        nearestNatX = nodeLat;
                        nearestNatY = nodeLon;
                    }
                }
            }
            else
            {
                double nodeLat = ws.nodesX.get(0);
                double nodeLon = ws.nodesY.get(0);
                double mag = Math.sqrt(Math.pow(lat - nodeLat, 2) + Math.pow(lon - nodeLon, 2));

                if(mag < magUnNat)
                {
                    magUnNat = mag;
                    nearestUnNatX = nodeLat;
                    nearestUnNatY = nodeLon;
                }
            }
        }
    }

    //Desc: non-blocking function to initiate an update to the list of nearby water sources
    void updateWaterCache(RequestQueue queue, double lat, double lon, double manhat_rad)
    {
        isWaterCacheUpdated.set(false);
        //generate HTTP request
        double minlat = lat - manhat_rad;
        double maxlat = lat + manhat_rad;
        double minlon = lon - manhat_rad;
        double maxlon = lon + manhat_rad;

        String coords = "(" + minlat + "," + minlon + "," + maxlat + "," + maxlon + ")";

        String url = "https://overpass-api.de/api/interpreter?data=" +
                     "[out:json];(way[natural=\"water\"]" + coords + ";>;" +
                     "way[\"waterway\"]" + coords + ";>;" +
                     "node[\"amenity\"=\"drinking_water\"]" + coords + ";" +
                     ");out;";

        Log.d("DEBUG", url);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new OSMCallbacks(ReqType.WATER, this),
                new OSMCallbacks(ReqType.WATER, this));

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                0,
                -1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = findViewById(R.id.defaultView);

        sources = new ArrayList<>();

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);

        //updateWaterCache(queue, 32.88186, -117.23414, 0.005);
        Weather.getWeather(queue, 32.88186, -117.23414);
    }
}
