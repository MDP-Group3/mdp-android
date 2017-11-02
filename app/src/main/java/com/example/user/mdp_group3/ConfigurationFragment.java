package com.example.user.mdp_group3;

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
 * This fragment controls the communication of Bluetooth with other devices and
 * performs the configuration of the robot as well as map.
 */
public class ConfigurationFragment extends Fragment {

    //Debugging
    private static final String TAG = "ConfigurationFragment";

    // Intent request bluetooth codes
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
    private Button btnUturn;
    private Button go;
    private Button reset;
    private Button btnCalibrate;
    private ToggleButton tgBtn;
    private Button updateMap;
    private TextView robotStatus;
    private GridView gridView;

    /**
     * Robot configuration
     */
    private int robotCenter = -1;
    private char robotDir = 'N';
    //store the actual positions of robot coordinates in gridview
    private int[] robotCoordinate = new int[9];
    //store the initial robot Center setting so that after reset of map, robotCenter can be automatically set to robotInitalCenter
    private int robotInitialCenter=-1;

    private int waypointPos;

    /**
     * Used when configure robot starting Center and waypoint.
     * If value==true, the current setted grid has to be reset
     * before configuring the new starting Center and waypoint
     */
    private boolean waypointInitialize = false;
    private boolean startPointInitialize = false;

    /**
     * Store coordinates of each grid in the map
     */
    private String[] coordinates = new String[300];

    /**
     * Coordinate of waypoint and robot starting Center in the map
     */
    private String chosenWaypoint;
    private String chosenStartpoint;

    /**
     * Convert hexadecimal to binary
     */
    HashMap<Character, String> hex_dict = new HashMap<Character, String>();

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
    private BluetoothServiceProvider mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
        // Initialize coordinates of grids in the map
        String tempX, tempY;
        int assignIndex = 0;
        for (int i = 19; i >= 0; i--) {
            tempX = Integer.toString(i);
            if (tempX.length() == 1) {
                tempX = "0" + tempX;
            }
            for (int j = 0; j < 15; j++) {
                tempY = Integer.toString(j);
                if (tempY.length() == 1) {
                    tempY = "0" + tempY;
                }
                coordinates[assignIndex] = tempX + "," + tempY;
                assignIndex++;
            }
        }
        // Initialize hashmap to store the mapping of hexadecimal to binary
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
            if (mChatService.getState() == BluetoothServiceProvider.STATE_NONE) {
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
        gridView = (GridView) view.findViewById(R.id.gridview);
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        robotStatus = (TextView) view.findViewById(R.id.robotStatus);
        tgBtn = (ToggleButton) view.findViewById(R.id.toggleButton);
        mSendButton = (Button) view.findViewById(R.id.button_send);
        robotCmd1 = (Button) view.findViewById(R.id.robotCmd1);
        robotCmd2 = (Button) view.findViewById(R.id.robotCmd2);
        btnForward = (Button) view.findViewById(R.id.btnForward);
        btnLeft = (Button) view.findViewById(R.id.btnLeft);
        btnRight = (Button) view.findViewById(R.id.btnRight);
        btnBackward = (Button) view.findViewById(R.id.btnBackward);
        btnUturn = (Button)view.findViewById(R.id.btnUturn);
        btnCalibrate= (Button) view.findViewById(R.id.btnCalibrate);
        go = (Button) view.findViewById(R.id.go);
        reset = (Button) view.findViewById(R.id.btnReset);
        updateMap = (Button) view.findViewById(R.id.updateMap);
    }

    /**
     * Set the coordinates of grids in the map
     * and enable user to set the robot starting Center and waypoint
     */
    public void configureMap() {

        final GridViewAdapter adapter = new GridViewAdapter(coordinates, getActivity());
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View v, final int position, long id) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                alertDialogBuilder.setMessage("You have selected " + "(" + parent.getItemAtPosition(position) + ")");

                alertDialogBuilder.setNegativeButton("Set as Startpoint",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                if (validRobotCenter(position)) {
                                    if (startPointInitialize) {
                                        for (int aRobotCoordinate : robotCoordinate) {
                                            TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                                            test.setBackgroundColor(Color.GRAY);
                                        }
                                    }
                                    startPointInitialize = true;
                                    chosenStartpoint = (String) parent.getItemAtPosition(position);
                                    robotInitialCenter = position;
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
                                    for (int aRobotCoordinate : robotCoordinate) {
                                        TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                                        test.setBackgroundColor(Color.RED);
                                    }
                                    TextView robotHead;
                                    switch (robotDir) {
                                        case 'E':
                                            robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                                            robotHead.setBackgroundColor(Color.YELLOW);
                                            break;
                                        case 'W':
                                            robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                                            robotHead.setBackgroundColor(Color.YELLOW);
                                            break;
                                        case 'N':
                                            robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                                            robotHead.setBackgroundColor(Color.YELLOW);
                                            break;
                                        case 'S':
                                            robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                                            robotHead.setBackgroundColor(Color.YELLOW);
                                            break;
                                    }
                                } else {
                                    Toast.makeText(getActivity(), "invalid robot Center", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

                alertDialogBuilder.setNeutralButton("Set as Waypoint", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TextView wptv;
                        if (waypointInitialize) {
                            wptv = (TextView) gridView.getChildAt(waypointPos).findViewById(R.id.grid);
                            wptv.setBackgroundColor(Color.GRAY);
                        }
                        TextView waypoint = (TextView) gridView.getChildAt(position).findViewById(R.id.grid);
                        waypoint.setBackgroundColor(Color.CYAN);
                        waypointPos = position;
                        waypointInitialize = true;
                        chosenWaypoint = (String) parent.getItemAtPosition(position);
                        Log.i(TAG, "chosenWayPoint:" + chosenWaypoint);
                    }
                });

                alertDialogBuilder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        });
    }

    /**
     *For tilt sensing functionality in MainActivity
     * Only if robot Center is not -1, which means robot Center is valid
     * then tilt sensing is enabled.
     */
    public int getRobotCenter() {
        return robotCenter;
    }

    /**
     *Send the setting of robot starting Center, waypoint and robot direction to PC
     */
    public void sendSWPoint() {
        if (chosenStartpoint != null && chosenWaypoint != null) {
            Log.i(TAG, "SWP sent");
            String message = "SP" + chosenStartpoint.substring(0, 2) + chosenStartpoint.substring(3, 5) +
                    "WP" + chosenWaypoint.substring(0, 2) + chosenWaypoint.substring(3, 5);
            sendMessage("P" + message+ robotDir);
            Toast.makeText(getActivity(), "SWP sent", Toast.LENGTH_LONG).show();
        }
    }

    /**
     *Check validity of robot Center to inhibit invalid movement and invalid setting of robot starting location
     */
    public boolean validRobotCenter(int position) {
        if (position % 15 == 0 || position < 15 || position > 283 || (position + 1) % 15 == 0) {
            return false;
        } else return true;
    }

    /**
     *Store the new robot grid position in the map according to the new center
     */
    public void updateRobotCoordinates(int newCenter) {
        robotCoordinate[0] = newCenter;
        robotCoordinate[1] = newCenter - 1;
        robotCoordinate[2] = newCenter + 1;
        robotCoordinate[3] = newCenter - 14;
        robotCoordinate[4] = newCenter - 15;
        robotCoordinate[5] = newCenter - 16;
        robotCoordinate[6] = newCenter + 14;
        robotCoordinate[7] = newCenter + 15;
        robotCoordinate[8] = newCenter + 16;
    }

    /**
     *Move the robot forward in the map
     * Called when received message "F" or button "FORWARD" is pressed
     */
    public void robotMoveForward() {
        final int newCenter;
        // compute the new center of robot in case that robot moves forward
        switch (robotDir) {
            case 'N':
                newCenter = robotCenter - 15;
                break;
            case 'S':
                newCenter = robotCenter + 15;
                break;
            case 'E':
                newCenter = robotCenter + 1;
                break;
            case 'W':
                newCenter = robotCenter - 1;
                break;
            default:
                newCenter = robotCenter - 15;
        }
        // move robot forward in the map if the forward movement is valid
        if (validRobotCenter(newCenter)) {
            robotStatus.setText(getString(R.string.robotMoveForward));
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //The trail of robot is colored blue, indicating explored
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView prevRobotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        prevRobotGrid.setBackgroundColor(Color.BLUE);
                    }
                    robotCenter = newCenter;
                    updateRobotCoordinates(robotCenter);
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView robotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        robotGrid.setBackgroundColor(Color.RED);
                    }
                    TextView robotHead;
                    switch (robotDir) {
                        case 'E':
                            robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'W':
                            robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'N':
                            robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'S':
                            robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                    }
                    robotStatus.setText(getString(R.string.robotReady));
                }
            }, 100);
        } else {
            Toast.makeText(getActivity(), "Hit wall!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *Turn the robot left in the map
     * Called when received message "L" or button "LEFT" is pressed
     */
    public void robotTurnLeft() {
        robotStatus.setText(getString(R.string.robotTurnLeft));
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView tvRobot;
                switch (robotDir) {
                    case 'E':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'N';
                        break;
                    case 'W':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'S';
                        break;
                    case 'N':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'W';
                        break;
                    case 'S':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'E';
                        break;

                }
                robotStatus.setText(getString(R.string.robotReady));
            }
        }, 100);
    }

    /**
     *Turn the robot right in the map
     * Called when received message "R" or button "RIGHT" is pressed
     */
    public void robotTurnRight() {
        robotStatus.setText(getString(R.string.robotTurnRight));
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView tvRobot;
                switch (robotDir) {
                    case 'E':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'S';
                        break;
                    case 'W':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'N';
                        break;
                    case 'N':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'E';
                        break;
                    case 'S':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'W';
                        break;

                }
                robotStatus.setText(getString(R.string.robotReady));
            }
        }, 100);
    }

    /**
     *Move the robot backward in the map
     * Called when received message "B" or button "BACKWARD" is pressed
     */
    public void robotMoveBackward() {
        final int newCenter;
        // compute the new center of robot in case that robot moves backward
        switch (robotDir) {
            case 'N':
                newCenter = robotCenter + 15;
                break;
            case 'S':
                newCenter = robotCenter - 15;
                break;
            case 'E':
                newCenter = robotCenter - 1;
                break;
            case 'W':
                newCenter = robotCenter + 1;
                break;
            default:
                newCenter = robotCenter + 15;
        }
        // move robot forward in the map if the forward movement is valid
        if (validRobotCenter(newCenter)) {
            robotStatus.setText(getString(R.string.robotMoveBackward));
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //The trail of robot is colored blue, indicating explored
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.BLUE);
                    }
                    robotCenter = newCenter;
                    updateRobotCoordinates(robotCenter);
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        test.setBackgroundColor(Color.RED);
                    }
                    TextView robotHead;
                    switch (robotDir) {
                        case 'E':
                            robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'W':
                            robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'N':
                            robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                        case 'S':
                            robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                            robotHead.setBackgroundColor(Color.YELLOW);
                            break;
                    }
                    robotStatus.setText(getString(R.string.robotReady));
                }
            }, 100);
        } else {
            Toast.makeText(getActivity(), "Hit wall!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *Turn the robot 180 defress in the map
     * Called when received message "U" or button "U turn" is pressed
     */
    public void robotUturn() {
        robotStatus.setText(getString(R.string.robotTurning180));
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView tvRobot;
                switch (robotDir) {
                    case 'E':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'W';
                        break;
                    case 'W':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'E';
                        break;
                    case 'N':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'S';
                        break;
                    case 'S':
                        tvRobot = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.RED);
                        tvRobot = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                        tvRobot.setBackgroundColor(Color.YELLOW);
                        robotDir = 'N';
                        break;

                }
                robotStatus.setText(getString(R.string.robotReady));
            }
        }, 100);
    }


    public void newRobotMoveForward() {
        int newCenter;
        switch (robotDir) {
            case 'N':
                newCenter = robotCenter - 15;
                break;
            case 'S':
                newCenter = robotCenter + 15;
                break;
            case 'E':
                newCenter = robotCenter + 1;
                break;
            case 'W':
                newCenter = robotCenter - 1;
                break;
            default:
                newCenter = robotCenter - 15;
        }
        if (validRobotCenter(newCenter)) {
            robotStatus.setText(getString(R.string.robotMoveForward));
            for (int aRobotCoordinate : robotCoordinate) {
                TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                test.setBackgroundColor(Color.BLUE);
            }
            robotCenter = newCenter;
            updateRobotCoordinates(robotCenter);
            for (int aRobotCoordinate : robotCoordinate) {
                TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                test.setBackgroundColor(Color.RED);
            }
            TextView robotHead;
            switch (robotDir) {
                case 'E':
                    robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'W':
                    robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'N':
                    robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'S':
                    robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
            }
            robotStatus.setText(getString(R.string.robotReady));
        }
    }

    public void newRobotTurnLeft() {
        robotStatus.setText(getString(R.string.robotTurnLeft));
        TextView robotHead;
        switch (robotDir) {
            case 'E':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'N';
                break;
            case 'W':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'S';
                break;
            case 'N':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'W';
                break;
            case 'S':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'E';
                break;

        }
        robotStatus.setText(getString(R.string.robotReady));
    }

    public void newRobotTurnRight() {
        robotStatus.setText(getString(R.string.robotTurnRight));
        TextView robotHead;
        switch (robotDir) {
            case 'E':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'S';
                break;
            case 'W':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'N';
                break;
            case 'N':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'E';
                break;
            case 'S':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.RED);
                robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                robotDir = 'W';
                break;

        }
        robotStatus.setText(getString(R.string.robotReady));
    }

    public void newRobotMoveBackward() {
        int newCenter;
        switch (robotDir) {
            case 'N':
                newCenter = robotCenter + 15;
                break;
            case 'S':
                newCenter = robotCenter - 15;
                break;
            case 'E':
                newCenter = robotCenter - 1;
                break;
            case 'W':
                newCenter = robotCenter + 1;
                break;
            default:
                newCenter = robotCenter + 15;
        }
        if (validRobotCenter(newCenter)) {
            robotStatus.setText(getString(R.string.robotMoveBackward));
            for (int aRobotCoordinate : robotCoordinate) {
                TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                test.setBackgroundColor(Color.BLUE);
            }
            robotCenter = newCenter;
            updateRobotCoordinates(robotCenter);
            for (int aRobotCoordinate : robotCoordinate) {
                TextView test = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                test.setBackgroundColor(Color.RED);
            }
            TextView robotHead;
            switch (robotDir) {
                case 'E':
                    robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'W':
                    robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'N':
                    robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
                case 'S':
                    robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                    break;
            }
            robotStatus.setText(getString(R.string.robotReady));
        }
    }
/*
    public void moveRobotWithFullStr(final String fullStr) {
        StringBuilder sb=new StringBuilder(fullStr);
        while (!sb.toString().isEmpty()) {
            Handler handler=new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                }
            },100);
            char c=sb.charAt(0);
            sb.deleteCharAt(0);
            Log.d(TAG, "moveGet"+c);
            switch (c) {
                case 'F':
                {
                    int i=sb.charAt(0)-'0';
                    sb.deleteCharAt(0);
                    Log.d(TAG, "moveForward"+i);
                    for (int j=0; j<i; j++) {
                        newRobotMoveForward();
                        Log.d(TAG, "move"+c+j);
                    }
                    break;
                }
                case 'L':
                    newRobotTurnLeft();
                    Log.d(TAG, "moveL"+c);
                    break;
                case 'R':
                    newRobotTurnRight();
                    Log.d(TAG, "moveR"+c);
                    break;
                case 'B':
                    newRobotMoveBackward();
                    Log.d(TAG, "moveB"+c);
                    break;
            }
        }
    }
*/

    /**
     *Move robot during exploration
     *PC send new robot center, new robot head direction and the movement which leads to the
     *new centre and head direction.
     *Robot grid is set directly according to the new robot centre
     *Delay is set to each movement so that the movement is noticeable
     */
    public void refreshRobotLocation(final String newCenter, final String newHeadDir, final String movement) {
        switch (movement) {
            case "F":
                robotStatus.setText(getString(R.string.robotMoveForward));
                break;
            case "L":
                robotStatus.setText(getString(R.string.robotTurnLeft));
                break;
            case "R":
                robotStatus.setText(getString(R.string.robotTurnRight));
                break;
            case "B":
                robotStatus.setText(getString(R.string.robotMoveBackward));
                break;
            case "U":
                robotStatus.setText(getString(R.string.robotTurning180));
                break;
        }
        //The trail of robot is colored blue, indicating explored
        for (int aRobotCoordinate : robotCoordinate) {
            TextView prevRobotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
            prevRobotGrid.setBackgroundColor(Color.BLUE);
            if (aRobotCoordinate == waypointPos) {
                prevRobotGrid.setBackgroundColor(Color.CYAN);
            }
        }
        int x = Integer.parseInt(newCenter.substring(0, 2));
        int y = Integer.parseInt(newCenter.substring(2, 4));
        if ((15 * x + y) < 284 && (15 * x + y) >= 16) {
            robotCenter = 15 * x + y;
        }
        updateRobotCoordinates(robotCenter);
        for (int aRobotCoordinate : robotCoordinate) {
            TextView robotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
            robotGrid.setBackgroundColor(Color.RED);
        }
        robotDir = newHeadDir.charAt(0);
        TextView robotHead;
        switch (robotDir) {
            case 'E':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                break;
            case 'W':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 1).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                break;
            case 'N':
                robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                break;
            case 'S':
                robotHead = (TextView) gridView.getChildAt(robotCenter + 15).findViewById(R.id.grid);
                robotHead.setBackgroundColor(Color.YELLOW);
                break;
        }
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                robotStatus.setText(getString(R.string.robotReady));
            }
        }, 1000);

    }

    /**
     *Convert hexadecimal string to binary string using the hashmap
     * which maps the hexa value to binary value
     */
    public String convertToBinary(String readMessage) {
        String binary = "";
        for (int i = 0; i < readMessage.length(); i++) {
            binary += hex_dict.get(readMessage.charAt(i));
        }
        return binary;
    }

    /**
     *Configure map during exploration
     * process part 1 MDF
     */
    public void processMDF(String part1Str, String part2Str) {
        String binaryPart1Str = convertToBinary(part1Str).substring(2, 302);
        StringBuilder sb = new StringBuilder(binaryPart1Str);
        char[][] ex_ref = new char[20][15];
        // part 2 MDF is processed with padding before sending to bluetooth
        // occurOne stores the number of '1' occurence to remove the padding when process part 2 MDF
        int occurOne = 0;
        for (int i = 0; i < ex_ref.length; i++) {
            for (int j = 0; j < ex_ref[i].length; j++) {
                ex_ref[i][j] = sb.charAt(0);
                if (sb.charAt(0) == '1') {
                    // process MDF part 1 string since MDF string describes map from bottom to top, row by row
                    // but gridview is interated from top to bottom
                    TextView tv = (TextView) gridView.getChildAt(j + (15 * (19 - i))).findViewById(R.id.grid);
                    ColorDrawable drawable = (ColorDrawable) tv.getBackground();
                    if (drawable.getColor() == Color.GRAY) {
                        tv.setBackgroundColor(Color.BLUE);
                    }
                    occurOne++;
                }
                sb.deleteCharAt(0);
            }
        }
        processPart2Str(part2Str, occurOne, ex_ref);
    }

    /**
     *Configure map during exploration
     * process part 2 MDF
     */
    public void processPart2Str(String str, int occurOne, char[][] ex_ref) {
        int pointedIndex = 0;
        String convertedBinaryPart2Str = convertToBinary(str);
        int padding = 8 - (occurOne % 8);
        convertedBinaryPart2Str = convertedBinaryPart2Str.substring(padding);
        for (int i = 0; i < ex_ref.length; i++) {
            for (int j = 0; j < ex_ref[i].length; j++) {
                if (ex_ref[i][j] == '1') {
                    char c = convertedBinaryPart2Str.charAt(pointedIndex);
                    pointedIndex++;
                    if (c == '0')
                        ex_ref[i][j] = '1';
                    else
                        ex_ref[i][j] = '2';
                }

            }
        }
        int multiplier = 19;
        for (int i = 0; i < ex_ref.length; i++) {
            for (int j = 0; j < ex_ref[i].length; j++) {
                TextView tv = (TextView) gridView.getChildAt(j + (15 * (multiplier - i))).findViewById(R.id.grid);
                if (ex_ref[i][j] == '2') {
                    tv.setBackgroundColor(Color.BLACK);
                }
                else if (ex_ref[i][j] == '1' ){
                    ColorDrawable drawable = (ColorDrawable) tv.getBackground();
                    if (drawable.getColor()!=Color.RED && drawable.getColor()!=Color.YELLOW && drawable.getColor()!=Color.CYAN) {
                        tv.setBackgroundColor(Color.BLUE);
                    }
                }
            }
        }
    }

     /**
     *Move robot during fastest path
     *During fastest path, full string of robot movement is received.
     *The string is consisted of L, R, B and F appended with n where n is
     *number of forward movement
     *Delay is set to each movement so that the movement is noticeable
     */
    public void moveRobotWithFullStr(final String fullStr) {
        final int[] i = {0};
        final Handler handler=new Handler();
        Runnable runnable=new Runnable() {
            @Override
            public void run() {
                if (i[0]<fullStr.length()) {
                    char c=fullStr.charAt(i[0]);
                    switch (c) {
                        case 'F':{
                            i[0]=i[0]+1;
                            int n=fullStr.charAt(i[0])-'0';
                            forwardWithDelay(n);
                            break;
                        }
                        case 'L':
                            newRobotTurnLeft();
                            break;
                        case 'R':
                            newRobotTurnRight();
                            break;
                        case 'B':
                            newRobotMoveBackward();
                            break;

                    }
                    i[0]=i[0]+1;
                    handler.postDelayed(this, 1100);
                }
            }
        };
        handler.post(runnable);
    }

    /**
     *Move the robot forward by f times
     *f is the number of forward movement
     *Delay is set to each movement so that the movement is noticeable
     */
    public void forwardWithDelay(final int f) {
        final int[] i = {0};
        final Handler handler=new Handler();
        Runnable runnable=new Runnable() {
            @Override
            public void run() {
                if (i[0]<f) {
                    newRobotMoveForward();
                    i[0]=i[0]+1;
                    handler.postDelayed(this, 110);
                }
            }
        };
        handler.post(runnable);
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

        //set the robot starting center and waypoint according to user choice if gridView is clicked
        configureMap();

        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSWPoint();
            }
        });

        // Make update map button visibile when manual mode is set, otherwise invisible
        tgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tgBtn.isChecked()) {
                    updateMap.setVisibility(View.VISIBLE);
                }
                else {
                    updateMap.setVisibility(View.INVISIBLE);
                }
            }
        });

        //send instruction to Arduino to calibrate, used before exploration starts
        btnCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage("AC");
            }
        });

        //reset map to totally unexplored condition and reset current robot center to the previously set robot starting center
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < 300; i++) {
                    TextView grid = (TextView) gridView.getChildAt(i).findViewById(R.id.grid);
                    grid.setBackgroundColor(Color.GRAY);
                }
                if (startPointInitialize) {
                    robotDir = 'N';
                    robotCenter = robotInitialCenter;
                    updateRobotCoordinates(robotInitialCenter);
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView robotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        robotGrid.setBackgroundColor(Color.RED);
                    }
                    TextView robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                } else {
                    robotDir = 'N';
                    robotCenter = 15 * 18 + 1;
                    updateRobotCoordinates(robotCenter);
                    for (int aRobotCoordinate : robotCoordinate) {
                        TextView robotGrid = (TextView) gridView.getChildAt(aRobotCoordinate).findViewById(R.id.grid);
                        robotGrid.setBackgroundColor(Color.RED);
                    }
                    TextView robotHead = (TextView) gridView.getChildAt(robotCenter - 15).findViewById(R.id.grid);
                    robotHead.setBackgroundColor(Color.YELLOW);
                }

            }
        });

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                String message = textView.getText().toString();
                if (message.startsWith("cmd1")) {
                    SharedPreferences.Editor editor = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString("cmd1", message.substring(4));
                    editor.apply();
                    sendMessage("P" + message.substring(4));
                    Toast.makeText(getActivity(), "Command1 Updated", Toast.LENGTH_SHORT).show();
                } else if (message.startsWith("cmd2")) {
                    SharedPreferences.Editor editor = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString("cmd2", message.substring(4));
                    editor.apply();
                    sendMessage("P" + message.substring(4));
                    Toast.makeText(getActivity(), "Command2 Updated", Toast.LENGTH_SHORT).show();
                } else {
                    sendMessage("P" + message);
                }
            }
        });

        robotCmd1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send message using content of the edit text widget
                // used to store exploration instruction
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                String restoredText = sharedPref.getString("cmd1", null);
                if (restoredText != null) {
                    sendMessage("P" + restoredText);
                } else {
                    Toast.makeText(getActivity(), "Command Undefined", Toast.LENGTH_SHORT).show();
                }

            }
        });
        robotCmd2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send message using content of the edit text widget
                //used to store fastest path instruction
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                String restoredText = sharedPref.getString("cmd2", null);
                if (restoredText != null) {
                    sendMessage("P" + restoredText);
                } else {
                    Toast.makeText(getActivity(), "Command Undefined", Toast.LENGTH_SHORT).show();
                }

            }
        });

        //when button "FORWARD" clicked, robot on the map moves forward and message is sent to Arduino
        //to move actual robot at the same time
        btnForward.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (robotCenter != -1) {
                    sendMessage("AF");
                    robotMoveForward();
                }
            }
        });

        //when button "LEFT" clicked, robot on the map turns left and message is sent to Arduino
        //to move actual robot at the same time
        btnLeft.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (robotCenter != -1) {
                    sendMessage("AL");
                    robotTurnLeft();
                }
            }
        });

        //when button "RIGHT" clicked, robot on the map turns right and message is sent to Arduino
        //to move actual robot at the same time
        btnRight.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (robotCenter != -1) {
                    sendMessage("AR");
                    robotTurnRight();
                }
            }
        });

        //when button "BACKWARD" clicked, robot on the map moves backward and message is sent to Arduino
        //to move actual robot at the same time
        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (robotCenter != -1) {
                    sendMessage("AB");
                    robotMoveBackward();
                }
            }
        });

        //when button "U TURN" clicked, robot on the map does u-turn and message is sent to Arduino
        //to move actual robot at the same time
        btnUturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (robotCenter!= -1) {
                    sendMessage("AU");
                    robotUturn();
                }
            }
        });

        // Initialize the BluetoothServiceProvider to perform bluetooth connections
        mChatService = new BluetoothServiceProvider(getActivity(), mHandler);

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
        if (mChatService.getState() != BluetoothServiceProvider.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothServiceProvider to write
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

    /**
     * The Handler that gets information back from the BluetoothServiceProvider
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothServiceProvider.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothServiceProvider.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothServiceProvider.STATE_LISTEN:
                        case BluetoothServiceProvider.STATE_NONE:
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
                    if (readMessage.length() == 1) {
                        switch (readMessage) {
                            case "F":
                                robotMoveForward();
                                break;
                            case "B":
                                robotMoveBackward();
                                break;
                            case "L":
                                robotTurnLeft();
                                break;
                            case "R":
                                robotTurnRight();
                                break;
                        }
                    }
                    //Message received from PC during exploration
                    //Message format: ***C,H,M;<MDF part 1 string>;<MDF part 2 string>
                    //where *** is the message header for MDF part 1 string
                    //C is new robot center
                    //H is new robot head direction
                    //M is robot movement
                    else if (readMessage.length() >= 3 && readMessage.substring(0, 3).equals("***")) {
                        readMessage = readMessage.substring(3);
                        String[] messages = readMessage.split(":");
                        String[] robotMsg = messages[0].split(",");
                        if (!tgBtn.isChecked()) {
                            refreshRobotLocation(robotMsg[0], robotMsg[1], robotMsg[2]);
                            processMDF(messages[1], messages[2]);
                        }
                    }
                    //Message received from PC during fastest path
                    //full string containing all movements is received
                    //consisted of B, L, R, and F appended with n where n is number of forward
                    else if (readMessage.length()>= 4 && readMessage.substring(0, 4).equals("FULL")) {
                        readMessage=readMessage.substring(4);
                        moveRobotWithFullStr(readMessage);

                    }
                    //To clear checklist(auto update of map), ADM tool string format is header AA with full string of map
                    // 1 directly represent obstacle
                    else if (readMessage.substring(0, 2).equals("AA") && !tgBtn.isChecked()) {
                        String binary = convertToBinary(readMessage.substring(2));
                        TextView tvObstacle;
                        for (int i = 0; i < binary.length(); i++) {
                            if (binary.charAt(i) == '1') {
                                tvObstacle = (TextView) gridView.getChildAt(i).findViewById(R.id.grid);
                                tvObstacle.setBackgroundColor(Color.BLACK);
                            } else {
                                tvObstacle = (TextView) gridView.getChildAt(i).findViewById(R.id.grid);
                                tvObstacle.setBackgroundColor(Color.GRAY);
                            }
                        }
                    }
                    //To clear checklist(manual update of map), ADM tool string format is header AA with full string of map
                    // 1 directly represent obstacle
                    else if (readMessage.substring(0, 2).equals("AA") && tgBtn.isChecked()) {
                        final String str = readMessage.substring(2);
                        updateMap.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String binary = convertToBinary(str);
                                TextView tvObstacle;
                                for (int i = 0; i < binary.length(); i++) {
                                    if (binary.charAt(i) == '1') {
                                        tvObstacle = (TextView) gridView.getChildAt(i).findViewById(R.id.grid);
                                        tvObstacle.setBackgroundColor(Color.BLACK);
                                    } else {
                                        tvObstacle = (TextView) gridView.getChildAt(i).findViewById(R.id.grid);
                                        tvObstacle.setBackgroundColor(Color.GRAY);
                                    }
                                }
                            }
                        });
                    }
                    else {
                        Log.i(TAG, "2AMDTool" + readMessage);
                        Toast.makeText(getActivity(), readMessage, Toast.LENGTH_SHORT).show();
                    }

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