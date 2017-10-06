/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.util.DisplayMetrics;
import android.util.Log;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.util.HashMap;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    private Button robotCmd1;
    private Button robotCmd2;
    private Button btnForward;
    private Button btnLeft;
    private Button btnRight;
    private Button btnBackward;
    private Button go;
    private ToggleButton tgBtn;
    private TextView robotStatus;
    private int[] robotCoordinate=new int[9];
    private int robotCenter;
    private char robotDir='N';
    private int waypointPos;
    private boolean waypointInitialize=false;
    private boolean startPointInitialize=false;
    private GridView gridView;
    private String chosenWaypoint="try";
    private String chosenStartpoint="try";

    private String binaryPart1Str=null;

    private String[] coordinates=new String[300];

    HashMap<Character,String> hex_dict = new HashMap<Character, String>();

    private OrientationData orientationData;
    private Long frameTime;
    private String[] messages=new String[3];

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String tempX, tempY;
        int assignIndex=0;
        for (int i=19; i>=0; i--) {
            tempX=Integer.toString(i);
            if (tempX.length()==1) {
                tempX="0"+tempX;
            }
            for (int j=0; j<15; j++) {
                tempY=Integer.toString(j);
                if (tempY.length()==1) {
                    tempY="0"+tempY;
                }
                coordinates[assignIndex]=tempX+","+tempY;
                assignIndex++;
            }
            hex_dict.put('0', "0000");
            hex_dict.put('1', "0001");
            hex_dict.put('2', "0010");
            hex_dict.put('3', "0011");
            hex_dict.put('4', "0100");
            hex_dict.put('5', "0101");
            hex_dict.put('6', "0110");
            hex_dict.put('7', "0111");
            hex_dict.put('8', "1000");
            hex_dict.put('9', "1001");
            hex_dict.put('a', "1010");
            hex_dict.put('b', "1011");
            hex_dict.put('c', "1100");
            hex_dict.put('d', "1101");
            hex_dict.put('e', "1110");
            hex_dict.put('f', "1111");
        }

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }


    @Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
        robotCmd1 = (Button) view.findViewById(R.id.robotCmd1);
        robotCmd2 = (Button) view.findViewById(R.id.robotCmd2);
        gridView = (GridView) view.findViewById(R.id.gridview);
        btnForward= (Button)view.findViewById(R.id.btnForward);
        btnLeft= (Button)view.findViewById(R.id.btnLeft);
        btnRight= (Button)view.findViewById(R.id.btnRight);
        btnBackward= (Button)view.findViewById(R.id.btnBackward);
        robotStatus=(TextView)view.findViewById(R.id.robotStatus);
        go=(Button)view.findViewById(R.id.go);
        tgBtn=(ToggleButton)view.findViewById(R.id.toggleButton);
    }

    public void configureMap(){
        Log.i(TAG, "configuremap");
        final GridViewAdapter adapter = new GridViewAdapter(coordinates, getActivity());
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View v, final int position, long id) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                alertDialogBuilder.setMessage("You have selected "+"("+(String) parent.getItemAtPosition(position)+")");
                String[] posArr=((String) parent.getItemAtPosition(position)).split(",");
                boolean manual=tgBtn.isChecked();
                if (!manual) {
                    alertDialogBuilder.setNegativeButton("Set as Startpoint",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface arg0, int arg1) {
                                    if (validRobotCentre(position)) {
                                        if (startPointInitialize) {
                                            for (int i = 0; i < robotCoordinate.length; i++) {
                                                TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                                                test.setBackgroundColor(Color.BLUE);
                                            }
                                        }
                                        startPointInitialize=true;
                                        adapter.chosenPositions.add(position);
                                        chosenStartpoint=(String) parent.getItemAtPosition(position);
                                        robotCenter = position;
                                        robotCoordinate[0] = position;
                                        robotCoordinate[1] = position - 1;
                                        robotCoordinate[2] = position + 1;
                                        robotCoordinate[3] = position - 14;
                                        robotCoordinate[4] = position - 15;
                                        robotCoordinate[5] = position - 16;
                                        robotCoordinate[6] = position + 14;
                                        robotCoordinate[7] = position + 15;
                                        robotCoordinate[8] = position + 16;
                                        for (int i = 0; i < robotCoordinate.length; i++) {
                                            TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                                            test.setBackgroundColor(Color.RED);
                                        }
                                        TextView startDTv;
                                        switch (robotDir) {
                                            case 'E':
                                                startDTv = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                                                startDTv.setBackgroundColor(Color.YELLOW);
                                                break;
                                            case 'W':
                                                startDTv = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                                                startDTv.setBackgroundColor(Color.YELLOW);
                                                break;
                                            case 'N':
                                                startDTv = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                                                startDTv.setBackgroundColor(Color.YELLOW);
                                                break;
                                            case 'S':
                                                startDTv = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                                                startDTv.setBackgroundColor(Color.YELLOW);
                                                break;


                                        }
                                    }
                                    else {
                                        Toast.makeText(getActivity(), "invalid robot centre", Toast.LENGTH_SHORT).show();
                                    }

                                }
                            });

                    alertDialogBuilder.setNeutralButton("Set as Waypoint", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            TextView wptv;
                            if (waypointInitialize) {
                                wptv = (TextView) gridView.getChildAt(waypointPos).findViewById(R.id.grid);
                                wptv.setBackgroundColor(Color.BLUE);
                            }
                            TextView waypoint = (TextView) gridView.getChildAt(position).findViewById(R.id.grid);
                            waypoint.setBackgroundColor(Color.CYAN);
                            waypointPos = position;
                            waypointInitialize = true;
                            chosenWaypoint=(String) parent.getItemAtPosition(position);
                            Log.i(TAG, "chosenWayPoint:"+chosenWaypoint);
                            adapter.chosenPositions.add(position);
                        }
                    });


                    alertDialogBuilder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                }
                else {
                    final TextView tv=(TextView) gridView.getChildAt(position).findViewById(R.id.grid);
                    ColorDrawable drawable = (ColorDrawable) tv.getBackground();
                    if(drawable.getColor()==Color.BLACK){
                        alertDialogBuilder.setNegativeButton("Remove Obstacle", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                tv.setBackgroundColor(Color.BLUE);
                                sendMessage("PGRID_R"+tv.getText());
                            }
                        });
                        alertDialogBuilder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                    }
                    else{
                        alertDialogBuilder.setNegativeButton("Set as Obstacle", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                tv.setBackgroundColor(Color.BLACK);
                                sendMessage("PGRID_S"+tv.getText());
                            }
                        });
                        alertDialogBuilder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                    }
                }
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        });
    }

    public boolean validRobotCentre(int position) {
        if (position%15==0 || position<15 || position>283 || (position+1)%15==0) {
            return false;
        }
        else return true;
    }

    public void sendSWPoint() {
        if (chosenStartpoint!=null && chosenWaypoint!=null) {
            Log.i(TAG, "SWP sent");
            String message="SP"+chosenStartpoint.substring(0,2)+chosenStartpoint.substring(3,5)+
                    "WP"+chosenWaypoint.substring(0, 2)+chosenWaypoint.substring(3,5);
            sendMessage("P"+message);
            Toast.makeText(getActivity(), "SWP sent", Toast.LENGTH_LONG).show();
        }

    }

    public void sensor() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int scHeight = displayMetrics.heightPixels;
        int scWidth = displayMetrics.widthPixels;
    //    if (frameTime<Constants.INTI_TIME) {
     //       frameTime=Constants.INTI_TIME;
     //   }
        frameTime=System.currentTimeMillis();
        Long elapsedTime=(int)System.currentTimeMillis()-frameTime;
        boolean success=orientationData.getOrientation()!=null && orientationData.getStartOrientation()!=null;
        if (success) {
            float pitch=orientationData.getOrientation()[1]-orientationData.getStartOrientation()[1];
            float roll=orientationData.getOrientation()[2]-orientationData.getStartOrientation()[2];
            float xSpeed=2*roll*scWidth/1000f;
            float ySpeed=pitch*scHeight/1000f;
            Log.i(TAG, "xp"+xSpeed);
            Log.i(TAG, "yp"+ySpeed);
        }
    }

    public void robotMoveForward() {
        final int newCenter;
        switch (robotDir)
        {
            case 'N':
                newCenter=robotCenter-15;
                break;
            case 'S':
                newCenter=robotCenter+15;
                break;
            case 'E':
                newCenter=robotCenter+1;
                break;
            case 'W':
                newCenter=robotCenter-1;
                break;
            default:
                newCenter=robotCenter-15;
        }
        if (validRobotCentre(newCenter)) {
            robotStatus.setText("Robot is Moving Forward");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    for(int i=0;i<robotCoordinate.length;i++) {
                        TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.BLUE);
                    }
                    robotCenter=newCenter;
                    updateRobotCoordinates(robotCenter);
                    for(int i=0;i<robotCoordinate.length;i++) {
                        TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.RED);
                    }
                    TextView startDTv;
                    switch (robotDir){
                        case 'E':
                            startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'W':
                            startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'N':
                            startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'S':
                            startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                    }
                    robotStatus.setText("Robot is ready!");
                }
            }, 100);
        }
        else {
            Toast.makeText(getActivity(), "Hit wall!", Toast.LENGTH_SHORT).show();
        }

    }

    public void robotTurnLeft() {
        robotStatus.setText("Robot is Moving Left");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView startDTv;
                switch (robotDir){
                    case 'E':
                        startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='N';
                        break;
                    case 'W':
                        startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='S';
                        break;
                    case 'N':
                        startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='W';
                        break;
                    case 'S':
                        startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='E';
                        break;

                }
                robotStatus.setText("Robot is ready!");
            }
        }, 100);
    }

    public void robotTurnRight() {
        robotStatus.setText("Robot is Moving Right");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView startDTv;
                switch (robotDir){
                    case 'E':
                        startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='S';
                        break;
                    case 'W':
                        startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='N';
                        break;
                    case 'N':
                        startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='E';
                        break;
                    case 'S':
                        startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.RED);
                        startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                        startDTv.setBackgroundColor(Color.YELLOW);
                        robotDir='W';
                        break;

                }
                robotStatus.setText("Robot is ready!");
            }
        }, 100);
    }

    public void robotMoveBackward() {
        int newCenter;
        switch (robotDir)
        {
            case 'N':
                newCenter=robotCenter+15;
                break;
            case 'S':
                newCenter=robotCenter-15;
                break;
            case 'E':
                newCenter=robotCenter-1;
                break;
            case 'W':
                newCenter=robotCenter+1;
                break;
            default:
                newCenter=robotCenter+15;
        }
        if (validRobotCentre(newCenter)) {
            robotStatus.setText("Robot is Moving Backward");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    for(int i=0;i<robotCoordinate.length;i++) {
                        TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.BLUE);
                    }
                    updateRobotCoordinates(robotCenter);
                    for(int i=0;i<robotCoordinate.length;i++) {
                        TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.RED);
                    }
                    TextView startDTv;
                    switch (robotDir){
                        case 'E':
                            startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'W':
                            startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'N':
                            startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'S':
                            startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                            startDTv.setBackgroundColor(Color.YELLOW);
                            break;
                    }
                    robotStatus.setText("Robot is ready!");
                }
            }, 100);
        }
        else {
            Toast.makeText(getActivity(), "Hit wall!", Toast.LENGTH_SHORT).show();
        }

    }

    public void updateRobotCoordinates(int newCenter){
        robotCoordinate[0]=newCenter;
        robotCoordinate[1]=newCenter-1;
        robotCoordinate[2]=newCenter+1;
        robotCoordinate[3]=newCenter-14;
        robotCoordinate[4]=newCenter-15;
        robotCoordinate[5]=newCenter-16;
        robotCoordinate[6]=newCenter+14;
        robotCoordinate[7]=newCenter+15;
        robotCoordinate[8]=newCenter+16;
    }

    public void refreshRobotLocation(String newCentre, String newHeadDir, String movement) {
        for(int i=0;i<robotCoordinate.length;i++) {
            TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
            test.setBackgroundColor(Color.BLUE);
        }
        int x=Integer.parseInt(newCentre.substring(0,2));
        int y=Integer.parseInt(newCentre.substring(2,4));
        if ((15*x+y)<284 && (15*x+y)>=16) {
            robotCenter=15*x+y;
        }
        updateRobotCoordinates(robotCenter);
        for(int i=0;i<robotCoordinate.length;i++) {
            TextView test = (TextView) gridView.getChildAt(robotCoordinate[i]).findViewById(R.id.grid);
            test.setBackgroundColor(Color.RED);
        }
        robotDir=newHeadDir.charAt(0);
        TextView startDTv;
        switch (robotDir){
            case 'E':
                startDTv = (TextView) gridView.getChildAt(robotCenter+1).findViewById(R.id.grid);
                startDTv.setBackgroundColor(Color.YELLOW);
                break;
            case 'W':
                startDTv = (TextView) gridView.getChildAt(robotCenter-1).findViewById(R.id.grid);
                startDTv.setBackgroundColor(Color.YELLOW);
                break;
            case 'N':
                startDTv = (TextView) gridView.getChildAt(robotCenter-15).findViewById(R.id.grid);
                startDTv.setBackgroundColor(Color.YELLOW);
                break;
            case 'S':
                startDTv = (TextView) gridView.getChildAt(robotCenter+15).findViewById(R.id.grid);
                startDTv.setBackgroundColor(Color.YELLOW);
                break;
        }
        switch (movement){
            case "F":
                robotStatus.setText("Robot is Moving Forward");
                break;
            case "L":
                robotStatus.setText("Robot is Moving Left");
                break;
            case "R":
                robotStatus.setText("Robot is Moving Right");
                break;
            case "B":
                robotStatus.setText("Robot is Moving Backward");
                break;
            default:
                robotStatus.setText("dd");
        }
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);


        configureMap();

        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSWPoint();
            }
        });

       // sensor();

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                String message = textView.getText().toString();
                if(message.startsWith("cmd1")){
                    SharedPreferences.Editor editor = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString("cmd1", message.substring(4));
                    editor.apply();
                    sendMessage("P"+message.substring(4));
                    Toast.makeText(getActivity(), "Command1 Updated", Toast.LENGTH_SHORT).show();
                }else if(message.startsWith("cmd2")){
                    SharedPreferences.Editor editor = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString("cmd2", message.substring(4));
                    editor.apply();
                    sendMessage("P"+message.substring(4));
                    Toast.makeText(getActivity(), "Command2 Updated", Toast.LENGTH_SHORT).show();
                }else{
                    sendMessage("P"+message);
                }
            }
        });

        robotCmd1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                String restoredText = sharedPref.getString("cmd1", null);
                if (restoredText != null) {
                    sendMessage("P"+restoredText);
                }
                else{
                    Toast.makeText(getActivity(), "Command Undefined", Toast.LENGTH_SHORT).show();
                }

            }
        });
        robotCmd2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                String restoredText = sharedPref.getString("cmd2", null);
                if (restoredText != null) {
                    sendMessage("P"+restoredText);
                }
                else{
                    Toast.makeText(getActivity(), "Command Undefined", Toast.LENGTH_SHORT).show();
                }

            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMessage("PF");
                robotMoveForward();
            }
        });

        btnLeft.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMessage("PL");
                robotTurnLeft();
            }
        });

        btnRight.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMessage("PR");
                robotTurnRight();
            }
        });

        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage("PB");
                robotMoveBackward();
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    public void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    public String convertToBinary(String readMessage) {
        String binary = "";
        for(int i = 0; i < readMessage.length(); i++) {
            binary += hex_dict.get(readMessage.charAt(i));
        }
        return binary;
    }

    public void configureRobotMovement(String movement) {
        switch (movement) {
            case "F":
                robotMoveForward();
                break;
            case "L":
                robotTurnLeft();
                break;
            case "R":
                robotTurnRight();
                break;
            case "B":
                robotMoveBackward();
                break;
        }
    }

    public void processMDF(String part1Str, String part2Str) {
        binaryPart1Str=convertToBinary(part1Str).substring(2,302);
        StringBuilder sb = new StringBuilder(binaryPart1Str);
        char[][] ex_ref = new char[20][15];
        int occurOne=0;
        for(int i = 0; i < ex_ref.length; i++) {
            for(int j = 0; j < ex_ref[i].length; j++) {
                ex_ref[i][j] = sb.charAt(0);
                if (sb.charAt(0)=='1') {
                    occurOne++;
                }
                sb.deleteCharAt(0);
            }
        }
        processPart2Str(part2Str, occurOne, ex_ref);
    }

    public void processPart2Str(String str, int occurOne, char[][]ex_ref) {
        int pointedIndex=0;
        String convertedBinaryPart2Str= convertToBinary(str);
        int padding = 8 - (occurOne % 8);
        convertedBinaryPart2Str = convertedBinaryPart2Str.substring(padding);
        for(int i = 0; i < ex_ref.length; i++) {
            for(int j = 0; j < ex_ref[i].length; j++) {
                if(ex_ref[i][j] == '1') {
                    char c = convertedBinaryPart2Str.charAt(pointedIndex);
                    pointedIndex++;
                    if(c == '0')
                        ex_ref[i][j] = '0';
                    else
                        ex_ref[i][j] = '1';
                }

            }
        }
        int multiplier = 19;
        for(int i = 0; i < ex_ref.length; i++) {
            for(int j = 0; j < ex_ref[i].length; j++) {
                if(ex_ref[i][j] == '1') {
                    TextView tvObstacle = (TextView) gridView.getChildAt(j + (15 * (multiplier - i))).findViewById(R.id.grid);
                    tvObstacle.setBackgroundColor(Color.BLACK);
                }
            }
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    messages=readMessage.split(":");
                  //  configureRobotMovement(messages[0]);
                    if (!tgBtn.isChecked()) {
                        String[] robotMsg=messages[0].split(",");
                        refreshRobotLocation(robotMsg[0], robotMsg[1],robotMsg[2]);
                        processMDF(messages[1], messages[2]);
                    }

                /*    if (!tgBtn.isChecked()) {
                        if (readMessage.length()==1) {
                            switch (readMessage) {
                                case "F":
                                    robotMoveForward();

                                    break;
                                case "L":
                                    robotTurnLeft();

                                    break;
                                case "R":
                                    robotTurnRight();

                                    break;
                                case "B":
                                    robotMoveBackward();

                                    break;
                            }
                        }
                        else if (readMessage.substring(0,2).equals("EX")) {

                                Log.i(TAG, "PR1");
                                readMessage=readMessage.substring(2);
                                Log.i(TAG, "EXreadmessage1"+readMessage);
                                Log.i(TAG, "EXreadmessage2"+readMessage.length());
                                binaryPart1Str=convertToBinary(readMessage).substring(2,302);
                                StringBuilder sb = new StringBuilder(binaryPart1Str);
                                for(int i = 0; i < ex_ref.length; i++) {
                                    for(int j = 0; j < ex_ref[i].length; j++) {
                                        ex_ref[i][j] = sb.charAt(0);
                                        if (sb.charAt(0)=='1') {
                                            occurOne++;
                                        }
                                        sb.deleteCharAt(0);
                                    }
                                }
                                BluetoothChatFragment.this.sendMessage("PR1");


                        }
                        else if (readMessage.substring(0,2).equals("OB")){
                            Log.i(TAG, "PR2");
                            int pointedIndex=0;
                            String convertedBinaryPart2Str= convertToBinary(readMessage.substring(2));
                            Log.i(TAG, "EEE" + convertedBinaryPart2Str.length());
                            int padding = 8 - (occurOne % 8);
                            occurOne=0;
                            convertedBinaryPart2Str = convertedBinaryPart2Str.substring(padding);

                            for(int i = 0; i < ex_ref.length; i++) {
                                for(int j = 0; j < ex_ref[i].length; j++) {
                                    if(ex_ref[i][j] == '1') {
                                        char c = convertedBinaryPart2Str.charAt(pointedIndex);
                                        pointedIndex++;
                                        if(c == '0')
                                            ex_ref[i][j] = '0';
                                        else
                                            ex_ref[i][j] = '1';
                                    }

                                }
                            }

                            int multiplier = 19;
                            for(int i = 0; i < ex_ref.length; i++) {
                                for(int j = 0; j < ex_ref[i].length; j++) {
                                    Log.i(TAG, "AAA"+Character.valueOf(ex_ref[0][7]));
                                    if(ex_ref[i][j] == '1') {
                                        Log.i(TAG, "test");
                                        TextView tvObstacle = (TextView) gridView.getChildAt(j + (15 * (multiplier - i))).findViewById(R.id.grid);
                                        tvObstacle.setBackgroundColor(Color.BLACK);
                                    }
                                }
                            }

                        }
                        else if (readMessage.substring(0,2).equals("CP")) {
                            refreshRobotLocation(readMessage.substring(2,6), readMessage.charAt(8));
                        }

                    }*/

                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}
