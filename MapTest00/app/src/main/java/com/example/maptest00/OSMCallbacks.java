package com.example.maptest00;

import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class OSMCallbacks implements Response.Listener<String>, Response.ErrorListener {

    MainActivity.ReqType type;
    Context ourContext;
    MainActivity activity;

    public OSMCallbacks(MainActivity.ReqType t, Context c, MainActivity act)
    {
        type = t;
        ourContext = c;
        activity = act;
    }

    @Override
    public void onResponse(String response)
    {
        Log.e("HTTP", "Returned message");
        MainActivity.cacheLock.lock();

        //delete prior data
        MainActivity.sources.clear();

        //String fakeWater = loadJSONFromAsset();

        try {

            JSONObject json = new JSONObject(response);

            ArrayList<Double> tempNodeX = new ArrayList<>();
            ArrayList<Double> tempNodeY = new ArrayList<>();
            ArrayList<Integer> tempNodeID = new ArrayList<>();

            //first read through json array and grab all elements
            JSONArray elems = json.getJSONArray("elements");
            for (int i = 0; i < elems.length(); i++) {
                JSONObject obj = elems.getJSONObject(i);

                if (obj.getString("type").equals("way")) {
                    //some sort of canal, stream, pond
                    WaterSource ws = new WaterSource(true);

                    //get the list of nodes
                    JSONArray nodeElems = obj.getJSONArray("nodes");

                    //add all of the node IDs
                    for (int j = 0; j < nodeElems.length(); j++) {
                        ws.nodesID.add(nodeElems.getInt(j));
                        ws.nodesX.add(-1.0); //filler value
                        ws.nodesY.add(-1.0); //filler value
                    }

                    MainActivity.sources.add(ws);
                } else if (obj.getString("type").equals("node")) {
                    //either a drinking fountain or part of a stream
                    if (obj.has("tags")) {
                        //a drinking fountain
                        WaterSource ws = new WaterSource(false);

                        ws.nodesID.add(obj.getInt("id"));
                        ws.nodesX.add(obj.getDouble("lat"));
                        ws.nodesY.add(obj.getDouble("lon"));

                        MainActivity.sources.add(ws);
                    } else {
                        //just a node for a stream
                        tempNodeID.add(obj.getInt("id"));
                        tempNodeX.add(obj.getDouble("lat"));
                        tempNodeY.add(obj.getDouble("lon"));
                    }
                }
            }

            //now match all nodes with its way
            for (WaterSource ws : MainActivity.sources) {
                if (!ws.isNatural)
                    continue;

                for (int i = 0; i < ws.nodesID.size(); i++) {
                    int idToMatch = ws.nodesID.get(i);
                    for (int j = 0; j < tempNodeID.size(); j++) {
                        if (idToMatch == tempNodeID.get(j)) {
                            ws.nodesX.set(i, tempNodeX.get(j));
                            ws.nodesY.set(i, tempNodeY.get(j));
                        }
                    }
                }
            }
        }
        catch(JSONException e)
        {
            Log.e("JSONERROR", e.getMessage());
        }

        MainActivity.cacheLock.unlock();
        MainActivity.isWaterCacheUpdated.set(true);

        activity.findClosestWater(activity.loc.getLatitude(), activity.loc.getLongitude());

        Log.e("XNATURAL", "x-" + MainActivity.nearestNatX);
        Log.e("YNATURAL", "y-" + MainActivity.nearestNatY);
        Log.e("XUNNAT", "x-" + MainActivity.nearestUnNatX);
        Log.e("YUNNAT", "y-" + MainActivity.nearestUnNatY);

        activity.updatePins();
    }

    @Override
    public void onErrorResponse(VolleyError error)
    {
        MainActivity.view.setText(error.getMessage());
    }

    // for testing only
    public String loadJSONFromAsset() {
        String json;
        try {
            InputStream is = ourContext.getAssets().open("test.json");

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }
}
