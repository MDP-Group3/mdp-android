package com.example.android.bluetoothchat;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

public class Grid extends FrameLayout{

    private TextView textView;

    public Grid(@NonNull Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.grid, this);
        textView = (TextView) getRootView().findViewById(R.id.grid);
    }

    public void showGrid(String text, boolean chosen) {
        textView.setText(text);
        show(chosen);
    }


    // help
    public void show(boolean chosen) {
        if (chosen) {
            textView.setBackgroundColor(Color.BLACK);
      //      textView.setOnClickListener(null);
        } else {
            textView.setBackgroundColor(Color.BLUE);
      //      textView.setOnClickListener(null);
        }
    }
}
