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

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.neosensory.neosensoryblessed.NeosensoryBLESSED;

public class MainActivity extends AppCompatActivity {
  private final String TAG = MainActivity.class.getSimpleName();
  private TextView NeoCLIOutput;
  private TextView NeoCLIHeader;
  private Button NeoConnectButton;
  private static final int REQUEST_ENABLE_BT = 1;
  private static final int ACCESS_LOCATION_REQUEST = 2;
  private static final int ACCESS_COARSE_LOCATION_REQUEST = 2;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setContentView(R.layout.activity_main);
    NeoCLIOutput = (TextView) findViewById(R.id.cli_response);
    NeoCLIOutput.setVisibility(View.INVISIBLE);
    NeoCLIHeader = (TextView) findViewById(R.id.cli_header);
    NeoCLIHeader.setVisibility(View.INVISIBLE);
    NeoConnectButton = (Button) findViewById(R.id.connection_button);
    NeoConnectButton.setVisibility(View.INVISIBLE);
    NeoConnectButton.setClickable(false);

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) return;
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
    if (hasPermissions()) {
      NeoConnectButton.setClickable(true);
      NeoConnectButton.setVisibility(View.VISIBLE);
      NeoConnectButton.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              initBluetoothHandler();
            }
          });
    }
  }

  private void initBluetoothHandler() {
    NeosensoryBLESSED.getInstance(getApplicationContext());
    // NeosensoryBLESSEDHandler.getInstance(getApplicationContext(),"EB:CA:85:38:19:1D");
    registerReceiver(CLIReceiver, new IntentFilter("CLIOutput"));
    registerReceiver(ConnectionStateReceiver, new IntentFilter("ConnectionState"));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(CLIReceiver);
    unregisterReceiver(ConnectionStateReceiver);
  }

  private final BroadcastReceiver ConnectionStateReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          Boolean connectedState = (Boolean) intent.getSerializableExtra("connectedState");
          if (connectedState == true) {
            NeoCLIOutput = (TextView) findViewById(R.id.cli_response);
            NeoCLIOutput.setVisibility(View.VISIBLE);
            NeoCLIHeader = (TextView) findViewById(R.id.cli_header);
            NeoCLIHeader.setVisibility(View.VISIBLE);
            NeoConnectButton = (Button) findViewById(R.id.connection_button);
            NeoConnectButton.setVisibility(View.INVISIBLE);
            NeoConnectButton.setClickable(false);
          }
        }
      };

  private final BroadcastReceiver CLIReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String notification_value = (String) intent.getSerializableExtra("CLIResponse");
          NeoCLIOutput.setText(notification_value);
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
