package com.neosensory.neosensoryblessedexample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
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
import com.neosensory.neosensoryblessed.NeosensoryBLESSED;

public class MainActivity extends AppCompatActivity {
  private final String TAG = MainActivity.class.getSimpleName();
  private TextView neoCLIOutput;
  private TextView neoCLIHeader;
  private Button neoConnectButton;
  private Button neoVibrateButton;
  private static final int REQUEST_ENABLE_BT = 1;
  private static final int ACCESS_LOCATION_REQUEST = 2;
  private static final int NUM_MOTORS = 4;
  private NeosensoryBLESSED blessedNeo = null;
  private static boolean vibrating = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Set up initial view state
    setContentView(R.layout.activity_main);
    setContentView(R.layout.activity_main);
    neoCLIOutput = (TextView) findViewById(R.id.cli_response);
    neoCLIOutput.setVisibility(View.INVISIBLE);
    neoCLIHeader = (TextView) findViewById(R.id.cli_header);
    neoCLIHeader.setVisibility(View.INVISIBLE);
    neoVibrateButton = (Button) findViewById(R.id.pattern_button);
    neoVibrateButton.setVisibility(View.INVISIBLE);
    neoVibrateButton.setClickable(false);
    neoConnectButton = (Button) findViewById(R.id.connection_button);
    neoConnectButton.setVisibility(View.INVISIBLE);
    neoConnectButton.setClickable(false);

    // Make sure Bluetooth is supported and has he needed permissions
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) return;
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
    if (hasPermissions()) {
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
  }

  private void initBluetoothHandler() {
    // Create and instance of the Bluetooth handler. This uses the constructor that will search for
    // and connect to the first available device with "Buzz" in its name. To connect to a specific
    // device with a specific address, you can use the following pattern:  blessedNeo =
    // NeosensoryBLESSED.getInstance(getApplicationContext(), <address> e.g."EB:CA:85:38:19:1D",
    // false);
    blessedNeo = NeosensoryBLESSED.getInstance(getApplicationContext(), false);
    // register Receiver's so that NeosensoryBLESSED can pass relevant messages to MainActivity
    registerReceiver(CLIReceiver, new IntentFilter("CLIOutput"));
    registerReceiver(ConnectionStateReceiver, new IntentFilter("ConnectionState"));
    registerReceiver(CLIReadyReceiver, new IntentFilter("CLIAvailable"));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(CLIReceiver);
    unregisterReceiver(ConnectionStateReceiver);
    unregisterReceiver(CLIReadyReceiver);
  }

  // The CLIReadyReceiver should receive a message anytime the connected Neosensory device changes
  // state between being ready to receive CLI commands and not
  private final BroadcastReceiver CLIReadyReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          // Create a Runnable (thread) to send a repeating vibrating pattern. Should terminate if
          // the variable `vibrating` is False
          class VibratingPattern implements Runnable {
            private int minVibration = 40;
            private int maxVibration = 255;
            private int currentVibration = minVibration;

            public void run() {
              // loop until the thread is interrupted
              int motorID = 0;
              while (!Thread.currentThread().isInterrupted() && vibrating) {
                try {
                  Thread.sleep(150);
                  byte[] motorPattern = new byte[4];
                  motorPattern[motorID] = (byte) currentVibration;
                  blessedNeo.vibrateMotors(motorPattern);
                  motorID = (motorID + 5) % NUM_MOTORS;
                  currentVibration = (currentVibration + 1) % maxVibration;
                  if (currentVibration == 0) {
                    currentVibration = minVibration;
                  }
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            }
          }
          // Check the message from Neosensory BLESSED to see if a Neosensory Command Line Interface
          // has become ready to accept commands
          Boolean CLIReady = (Boolean) intent.getSerializableExtra("CLIReady");
          // Prior to calling other API commands we need to accept the Neosensory API ToS
          if (CLIReady) {
            // request developer level access to the connected Neosensory device
            blessedNeo.sendDeveloperAPIAuth();
            // sendDeveloperAPIAuth() will then transmit a message back requiring an explicit
            // acceptance of Neosensory's Terms of Service located at
            // https://neosensory.com/legal/dev-terms-service/
            blessedNeo.acceptAPIToS();
            Log.i(TAG, String.format("state message", blessedNeo.getNeoCLIResponse()));
            // Assuming successful authorization, set up a button to run the vibrating pattern
            // thread above
            neoVibrateButton.setVisibility(View.VISIBLE);
            neoVibrateButton.setClickable(true);
            neoVibrateButton.setOnClickListener(
                new View.OnClickListener() {
                  public void onClick(View v) {
                    if (!vibrating) {
                      // Prior to sending custom motor vibrate commands, we'll need to stop the
                      // device's Audio task from running (assuming the device is a Neosensory Buzz)
                      blessedNeo.stopAudio();
                      // Generally good practice to clear the motor command queue prior to sending
                      // custom motor vibration commands
                      blessedNeo.clearMotorQueue();
                      blessedNeo.startMotors();
                      neoVibrateButton.setText("Stop Vibration Pattern");
                      vibrating = true;
                      // Create and run the vibrating pattern thread
                      Runnable vibing = new VibratingPattern();
                      new Thread(vibing).start();

                    } else {
                      neoVibrateButton.setText("Start Vibration Pattern");
                      vibrating = false;
                      blessedNeo.startAudio();
                    }
                  }
                });
          }
        }
      };

  // ConnectionStateReceiver is used to convey and process state changes whether or not a Neosensory
  // device is connected
  private final BroadcastReceiver ConnectionStateReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          Boolean connectedState = (Boolean) intent.getSerializableExtra("connectedState");
          if (connectedState == true) {
            neoCLIOutput.setVisibility(View.VISIBLE);
            neoCLIHeader.setVisibility(View.VISIBLE);
            neoConnectButton.setText("Disconnect");
            neoConnectButton.setOnClickListener(
                new View.OnClickListener() {
                  public void onClick(View v) {
                    blessedNeo.disconnectNeoDevice();
                  }
                });
          } else {
            vibrating = false; // Just to be sure the vibrating pattern thread terminates
            neoCLIOutput.setVisibility(View.INVISIBLE);
            neoCLIHeader.setVisibility(View.INVISIBLE);
            neoVibrateButton.setVisibility(View.INVISIBLE);
            neoVibrateButton.setClickable(false);
            neoConnectButton.setText("Scan and Connect to Neosensory Buzz");
            neoConnectButton.setOnClickListener(
                new View.OnClickListener() {
                  public void onClick(View v) {
                    blessedNeo.attemptNeoReconnect();
                  }
                });
          }
        }
      };
  // This BroadcastReceiver creates a notification whenever the connected device sends a response.
  // Alternatively, can use the NeosensoryBLESSED method getNeoCLIResponse() to obtain the most
  // recent response from the CLI.
  private final BroadcastReceiver CLIReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String notification_value = (String) intent.getSerializableExtra("CLIResponse");
          neoCLIOutput.setText(notification_value);
        }
      };

  private boolean hasPermissions() {
    int targetSdkVersion = getApplicationInfo().targetSdkVersion;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && targetSdkVersion >= Build.VERSION_CODES.Q) {
      if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST);
        return false;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_LOCATION_REQUEST);
        return false;
      }
    }
    return true;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode) {
      case ACCESS_LOCATION_REQUEST:
        if (grantResults.length > 0) {
          if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initBluetoothHandler();
          }
        }
        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        break;
    }
  }
}
