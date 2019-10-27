package com.example.maptest00;

import java.util.ArrayList;

public class WaterSource {

    public boolean isNatural;
    public String name;
    public ArrayList<Double> nodesX;
    public ArrayList<Double> nodesY;
    public ArrayList<Integer> nodesID;

    public WaterSource(boolean nat)
    {
        isNatural = nat;
        nodesID = new ArrayList<>();
        nodesX = new ArrayList<>();
        nodesY = new ArrayList<>();
    }
}
