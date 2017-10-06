package com.example.android.bluetoothchat;


import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

public class GridViewAdapter extends BaseAdapter {

    private Activity activity;
    private String[] strings;
    public List<Integer> chosenPositions;

    public GridViewAdapter(String[] strings, Activity activity) {
        this.strings = strings;
        this.activity = activity;
        chosenPositions = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return strings.length;
    }

    @Override
    public Object getItem(int position) {
        return strings[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Grid customView = (convertView == null) ? new Grid(activity) : (Grid) convertView;
        customView.showGrid(strings[position], false);
        return customView;
    }
}