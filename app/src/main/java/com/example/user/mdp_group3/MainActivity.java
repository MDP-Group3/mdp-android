package com.example.user.mdp_group3;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class MainActivity extends AppCompatActivity implements GestureDetector.OnGestureListener{

    public static final String TAG = "MainActivity";

    ConfigurationFragment fragment=null;

    private GestureDetectorCompat mDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            fragment = new ConfigurationFragment();
            mDetector = new GestureDetectorCompat(this,this);
            // Create an object of our Gesture Detector Class
            transaction.add(R.id.fragment_container, fragment).commit();
        }
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2,
                           float velocityX, float velocityY) {

        switch (getSlope(e1.getX(), e1.getY(), e2.getX(), e2.getY())) {
            case 1:
                android.util.Log.i(TAG, "top");
                if (fragment!=null && fragment.getRobotCenter()!=-1) {
                    fragment.robotMoveForward();
                }
                return true;
            case 2:
                android.util.Log.i(TAG, "left");
                if (fragment!=null && fragment.getRobotCenter()!=-1) {
                    fragment.robotTurnLeft();
                }
                return true;
            case 3:
                android.util.Log.i(TAG, "down");
                if (fragment!=null && fragment.getRobotCenter()!=-1) {
                    fragment.robotMoveBackward();
                }
                return true;
            case 4:
                android.util.Log.i(TAG, "right");
                if (fragment!=null && fragment.getRobotCenter()!=-1) {
                    fragment.robotTurnRight();
                }
                return true;
        }
        android.util.Log.i(TAG, "onFlingNoDetect");
        return false;
    }

    private int getSlope(float x1, float y1, float x2, float y2) {
        Double angle = Math.toDegrees(Math.atan2(y1 - y2, x2 - x1));
        if (angle > 45 && angle <= 135)
            // top
            return 1;
        if (angle >= 135 && angle < 180 || angle < -135 && angle > -180)
            // left
            return 2;
        if (angle < -45 && angle>= -135)
            // down
            return 3;
        if (angle > -45 && angle <= 45)
            // right
            return 4;
        return 0;
    }
}
