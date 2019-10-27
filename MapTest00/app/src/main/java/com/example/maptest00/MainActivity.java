package com.example.maptest00;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;


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

import java.util.List;
import java.util.ArrayList;

/**
 * Use the LocationComponent to easily add a device location "puck" to a Mapbox map.
 */
public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, PermissionsListener {

    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private ArrayList<Marker> markers = new ArrayList<Marker>();
    private Marker selectedMarker;
    private Vibrator vib;
    public LatLng loc;
    private RequestQueue rq;


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

    public void displayWeatherPopup()
    {
        popup("WEATHER INFORMATION\n\nat position\n\nLAT: " + loc.getLatitude() + "\nLONG: " + loc.getLongitude() + "\n\nTemperature: " + String.format("%.2f",(Double.valueOf(weather) - 273.15)) + " C\nLocation: " + location + "\nDescription: " + description);
    }

    public void updatePins()
    {
        if(!Double.isNaN(nearestNatX))
        {
            LatLng nearestNat = new LatLng(nearestNatX, nearestNatY);
            AddBasicMarker(nearestNat);
        }
        if(!Double.isNaN(nearestUnNatX))
        {
            LatLng nearestUnNat = new LatLng(nearestUnNatX, nearestUnNatY);
            AddBasicMarker(nearestUnNat);
        }



    }

    public void findClosestWater(double lat, double lon)
    {
        nearestUnNatX = NaN;
        nearestUnNatY = NaN;
        nearestNatX = NaN;
        nearestNatY = NaN;

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
                new OSMCallbacks(ReqType.WATER, this, this),
                new OSMCallbacks(ReqType.WATER, this, this));

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

        vib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        sources = new ArrayList<>();

        rq = Volley.newRequestQueue(this);

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.access_token));

        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {

        MainActivity.this.mapboxMap = mapboxMap;

        mapboxMap.addOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
            @Override
            public boolean onMapLongClick(@NonNull LatLng point) {
                RemoveAllMarkers();
                //Add Marker
                AddBasicMarker(point);
                CameraPosition position = new CameraPosition.Builder()
                        .target(point) // Sets the new camera position
                        .build(); // Creates a CameraPosition from the builder
                mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000);
                //haptic feedback
                vib.vibrate(75);
                loc = point;
                return false;
            }
        });

        mapboxMap.setStyle(Style.SATELLITE_STREETS,
                new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        enableLocationComponent(style);
                    }
                });
    }

    private double distance(LatLng p1, LatLng p2) {
        return Math.hypot(p1.getLatitude()-p2.getLatitude(), p1.getLongitude()-p2.getLongitude());
    }

    public void AddBasicMarker(LatLng pos)
    {
        markers.add(mapboxMap.addMarker(new MarkerOptions().position(pos)));
    }

    public void RemoveBasicMarker(LatLng pos)
    {
        for (Marker m : markers)
            if (m.getPosition() == pos)
                mapboxMap.removeMarker(m);
    }

    public void RemoveAllMarkers()
    {
        for (Marker m : markers)
            mapboxMap.removeMarker(m);
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            // Activate with options
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);

            updateLocation(mapboxMap.getLocationComponent());

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    @SuppressWarnings( {"MissingPermission"})
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    public void updateLocation(LocationComponent locationComponent)
    {
        loc = new LatLng(locationComponent.getLastKnownLocation().getLatitude(), locationComponent.getLastKnownLocation().getLongitude());
    }

    private void popup(String contents) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it

        final PopupWindow pw = new PopupWindow(inflater.inflate(R.layout.activity_popup, null, false), width, height, focusable);

        ((TextView)pw.getContentView().findViewById(R.id.popup_text)).setText(contents);

        pw.showAtLocation(mapView, Gravity.CENTER, 0, 0);
    }

    public void onWatClicked(View view) {
        vib.vibrate(75);

        //wait for HTTP request
        updateWaterCache(rq, loc.getLatitude(), loc.getLongitude(), 0.05);

        //popup("WATER INFORMATION\n\nat position\n\nLAT: " + loc.getLatitude() + "\nLONG: " + loc.getLongitude());
    }

    public void onAidClicked(View view) {
        vib.vibrate(75);
        popup("EMERGENCY INFORMATION\n\nat position\n\nLAT: " + loc.getLatitude() + "\nLONG: " + loc.getLongitude());
    }

    public void onNavClicked(View view) {
        vib.vibrate(75);
        popup("NAVIGATION INFORMATION\n\nat position\n\nLAT: " + loc.getLatitude() + "\nLONG: " + loc.getLongitude());
    }

    public void onTempClicked(View view) {
        vib.vibrate(75);
        Weather.getWeather(rq, loc.getLatitude(), loc.getLongitude(), this);
    }

    public void onTerrainClicked(View view) {
        vib.vibrate(75);
        popup("TERRAIN INFORMATION\n\nat position\n\nLAT: " + loc.getLatitude() + "\nLONG: " + loc.getLongitude());
    }
}