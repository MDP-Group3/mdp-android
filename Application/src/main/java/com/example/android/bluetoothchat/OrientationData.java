package com.example.android.bluetoothchat;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import static android.content.Context.SENSOR_SERVICE;

public class OrientationData implements SensorEventListener{

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnometer;
    private float[] accelOutput;
    private float[] magOutput;
    private float[] orientation;
    private float[] startOrientation=null;
    private final Context context;

    public OrientationData(Context context) {
        this.context=context;
        sensorManager = (SensorManager)context.getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void register(){
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magnometer, SensorManager.SENSOR_DELAY_GAME);
    }

    public void pause() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType()==Sensor.TYPE_ACCELEROMETER) {
            accelOutput=event.values;
        }
        else if (event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD) {
            magOutput=event.values;
        }
        if (accelOutput!=null && magOutput!=null) {
            float [] R=new float[9];
            float [] I=new float[9];
            if (SensorManager.getRotationMatrix(R, I, accelOutput, magOutput)) {
                SensorManager.getOrientation(R, orientation);
                if (startOrientation==null) {
                    startOrientation=new float[orientation.length];
                    System.arraycopy(orientation, 0, startOrientation, 0, orientation.length);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public float[] getOrientation() {
        return orientation;
    }

    public float[] getStartOrientation() {
        return startOrientation;
    }

    public void startRobot() {
        startOrientation=null;
    }
}
