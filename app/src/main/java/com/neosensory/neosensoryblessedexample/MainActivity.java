package com.neosensory.neosensoryblessedexample;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.neosensory.neosensoryblessed.NeosensoryBlessed;

public class MainActivity extends AppCompatActivity {
  // set string for filtering output for this activity in Logcat
  private final String TAG = MainActivity.class.getSimpleName();

  // UI Components
  private TextView neoCliOutput;
  private TextView neoCliHeader;
  private Button neoConnectButton;
  private Button neoVibrateButton;

  // Constants
  private static final int ACCESS_LOCATION_REQUEST = 2;
  private static final int NUM_MOTORS = 4;

  // Access the library to leverage the Neosensory API
  private NeosensoryBlessed blessedNeo = null;

  // Variable to track whether or not the wristband should be vibrating
  private static boolean vibrating = false;
  private static boolean disconnectRequested =
      false; // used for requesting a disconnect within our thread
  Runnable vibratingPattern;
  Thread vibratingPatternThread;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Get a lock on on the UI components (2 Textviews and 2 Buttons)
    setContentView(R.layout.activity_main);
    neoCliOutput = (TextView) findViewById(R.id.cli_response);
    neoCliHeader = (TextView) findViewById(R.id.cli_header);
    neoVibrateButton = (Button) findViewById(R.id.pattern_button);
    neoConnectButton = (Button) findViewById(R.id.connection_button);

    displayInitialUI();
    NeosensoryBlessed.requestBluetoothOn(this);
    if (checkLocationPermissions()) {
      displayInitConnectButton();
    } // Else, this function will have the system request permissions and handle displaying the
    // button in the callback onRequestPermissionsResult

    // Create the vibrating pattern thread (but don't start it yet)
    vibratingPattern = new VibratingPattern();
  }

  // Create a Runnable (thread) to send a repeating vibrating pattern. Should terminate if
  // the variable `vibrating` is False
  class VibratingPattern implements Runnable {
    private int minVibration = 40;
    private int currentVibration = minVibration;

    public void run() {
      // loop until the thread is interrupted
      int motorID = 0;
      while (!Thread.currentThread().isInterrupted() && vibrating) {
        try {
          Thread.sleep(150);
          int[] motorPattern = new int[4];
          motorPattern[motorID] = currentVibration;
          blessedNeo.vibrateMotors(motorPattern);
          motorID = (motorID + 1) % NUM_MOTORS;
          currentVibration = (currentVibration + 1) % NeosensoryBlessed.MAX_VIBRATION_AMP;
          if (currentVibration == 0) {
            currentVibration = minVibration;
          }
        } catch (InterruptedException e) {
          blessedNeo.stopMotors();
          blessedNeo.resumeDeviceAlgorithm();
          Log.i(TAG, "Interrupted thread");
          e.printStackTrace();
        }
      }
      if (disconnectRequested) {
        Log.i(TAG, "Disconnect requested while thread active");
        blessedNeo.stopMotors();
        blessedNeo.resumeDeviceAlgorithm();
        // When disconnecting: it is possible for the device to process the disconnection request
        // prior to processing the request to resume the onboard algorithm, which causes the last
        // sent motor command to "stick"
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        blessedNeo.disconnectNeoDevice();
        disconnectRequested = false;
      }
    }
  }

  //////////////////////////
  // Cleanup on shutdown //
  /////////////////////////

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(BlessedReceiver);
    if (vibrating) {
      vibrating = false;
      disconnectRequested = true;
    }
    blessedNeo = null;
    vibratingPatternThread = null;
  }

  ////////////////////////////////////
  // SDK state change functionality //
  ////////////////////////////////////

  // A Broadcast Receiver is responsible for conveying important messages/information from our
  // NeosensoryBlessed instance. There are 3 types of messages we can receive:
  //
  // 1. "com.neosensory.neosensoryblessed.CliReadiness": conveys a change in state for whether or
  // not a connected Buzz is ready to accept commands over its command line interface. Note: If the
  // CLI is ready, then it is currently a prerequisite that a compliant device is connected.
  //
  // 2. "com.neosensory.neosensoryblessed.ConnectedState": conveys a change in state for whether or
  // not we're connected to a device. True == connected, False == not connected. In this example, we
  // don't actually need this, because we can use the CLI's readiness by proxy.
  //
  // 3. "com.neosensory.neosensoryblessed.CliMessage": conveys a message sent to Android from a
  // connected Neosensory device's command line interface
  private final BroadcastReceiver BlessedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.hasExtra("com.neosensory.neosensoryblessed.CliReadiness")) {
            // Check the message from NeosensoryBlessed to see if a Neosensory Command Line
            // Interface
            // has become ready to accept commands
            // Prior to calling other API commands we need to accept the Neosensory API ToS
            if (intent.getBooleanExtra("com.neosensory.neosensoryblessed.CliReadiness", false)) {
              // request developer level access to the connected Neosensory device
              blessedNeo.sendDeveloperAPIAuth();
              // sendDeveloperAPIAuth() will then transmit a message back requiring an explicit
              // acceptance of Neosensory's Terms of Service located at
              // https://neosensory.com/legal/dev-terms-service/
              blessedNeo.acceptApiTerms();
              Log.i(TAG, String.format("state message: %s", blessedNeo.getNeoCliResponse()));
              // Assuming successful authorization, set up a button to run the vibrating pattern
              // thread above
              displayVibrateButton();
              displayDisconnectUI();
            } else {
              displayReconnectUI();
            }
          }

          if (intent.hasExtra("com.neosensory.neosensoryblessed.CliMessage")) {
            String notification_value =
                intent.getStringExtra("com.neosensory.neosensoryblessed.CliMessage");
            neoCliOutput.setText(notification_value);
          }

          if (intent.hasExtra("com.neosensory.neosensoryblessed.ConnectedState")) {
            if (intent.getBooleanExtra("com.neosensory.neosensoryblessed.ConnectedState", false)) {
              Log.i(TAG, "Connected to Buzz");
            } else {
              Log.i(TAG, "Disconnected from Buzz");
            }
          }
        }
      };

  ///////////////////////////////////
  // User interface functionality //
  //////////////////////////////////

  private void displayInitialUI() {
    displayReconnectUI();
    neoVibrateButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            if (!vibrating) {
              blessedNeo.pauseDeviceAlgorithm();
              neoVibrateButton.setText("Stop Vibration Pattern");
              vibrating = true;
              // run the vibrating pattern loop
              vibratingPatternThread = new Thread(vibratingPattern);
              vibratingPatternThread.start();
            } else {
              neoVibrateButton.setText("Start Vibration Pattern");
              vibrating = false;
              blessedNeo.resumeDeviceAlgorithm();
            }
          }
        });
  }

  private void displayReconnectUI() {
    neoCliOutput.setVisibility(View.INVISIBLE);
    neoCliHeader.setVisibility(View.INVISIBLE);
    neoVibrateButton.setVisibility(View.INVISIBLE);
    neoVibrateButton.setClickable(false);
    neoVibrateButton.setText(
        "Start Vibration Pattern"); // Vibration stops on disconnect so reset the button text
    neoConnectButton.setText("Scan and Connect to Neosensory Buzz");
    neoConnectButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            blessedNeo.attemptNeoReconnect();
            toastMessage("Attempting to reconnect. This may take a few seconds.");
          }
        });
  }

  private void displayDisconnectUI() {
    neoCliOutput.setVisibility(View.VISIBLE);
    neoCliHeader.setVisibility(View.VISIBLE);
    neoConnectButton.setText("Disconnect");
    neoConnectButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            if (!vibrating) {
              blessedNeo.disconnectNeoDevice();
            } else {
              // If motors are vibrating (in the VibratingPattern thread in this case) and we want
              // to stop them on disconnect, we need to add a sleep/delay as it's possible for the
              // disconnect to be processed prior to stopping the motors. See the VibratingPattern
              // definition.
              disconnectRequested = true;
              vibrating = false;
            }
          }
        });
  }

  private void displayInitConnectButton() {
    // Display the connect button and create the Bluetooth Handler if so
    neoConnectButton.setClickable(true);
    neoConnectButton.setVisibility(View.VISIBLE);
    neoConnectButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            initBluetoothHandler();
          }
        });
  }

  public void displayVibrateButton() {
    neoVibrateButton.setVisibility(View.VISIBLE);
    neoVibrateButton.setClickable(true);
  }

  private void toastMessage(String message) {
    Context context = getApplicationContext();
    int duration = Toast.LENGTH_LONG;
    Toast toast = Toast.makeText(context, message, duration);
    toast.show();
  }
  //////////////////////////////////////////////
  // Bluetooth and permissions initialization //
  //////////////////////////////////////////////

  private void initBluetoothHandler() {
    // Create an instance of the Bluetooth handler. This uses the constructor that will search for
    // and connect to the first available device with "Buzz" in its name. To connect to a specific
    // device with a specific address, you can use the following pattern:  blessedNeo =
    // NeosensoryBlessed.getInstance(getApplicationContext(), <address> e.g."EB:CA:85:38:19:1D",
    // false);
    blessedNeo =
        NeosensoryBlessed.getInstance(getApplicationContext(), new String[] {"Buzz"}, false);
    // register receivers so that NeosensoryBlessed can pass relevant messages and state changes to MainActivity
    registerReceiver(BlessedReceiver, new IntentFilter("BlessedBroadcast"));
  }

  private boolean checkLocationPermissions() {
    int targetSdkVersion = getApplicationInfo().targetSdkVersion;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && targetSdkVersion >= Build.VERSION_CODES.Q) {
      if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST);
        return false;
      } else {
        return true;
      }
    } else {
      if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_LOCATION_REQUEST);
        return false;
      } else {
        return true;
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if ((requestCode == ACCESS_LOCATION_REQUEST)
        && (grantResults.length > 0)
        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
      displayInitConnectButton();
    } else {
      toastMessage("Unable to obtain location permissions, which are required to use Bluetooth.");
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }
}
