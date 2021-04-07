package com.neosensory.neosensoryblessed;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.RequiresApi;

// imports from the blessed library
import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.WriteType;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;


import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;


public class NeosensoryBlessed {

  private final String TAG = NeosensoryBlessed.class.getSimpleName();

  private static final int REQUEST_ENABLE_BT = 1;
  public static final int MAX_VIBRATION_AMP = 255;
  public static final int MIN_VIBRATION_AMP = 0;

  // UUIDs for Neosensory UART over BLE
  private static final UUID UART_OVER_BLE_SERVICE_UUID =
      UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
  private static final UUID UART_RX_WRITE_UUID =
      UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
  private static final UUID UART_TX_NOTIFY_UUID =
      UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

  // UUIDs for the Device Information service (DIS)
  private static final UUID DIS_SERVICE_UUID =
      UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
  private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID =
      UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");

  // Local variables
  private BluetoothCentralManager central;
  private static NeosensoryBlessed instance = null;
  private Context context;
  private Handler handler = new Handler();
  private static BluetoothPeripheral neoPeripheral = null;
  private static BluetoothGattCharacteristic neoWriteCharacteristic = null;

  // State information
  private boolean autoReconnectEnabled;
  private boolean neoDeviceConnected = false;
  private boolean neoCliReady = false;
  private String neoCliResponse = "";

  private enum StatusUpdateType {
    CLIREADINESS,
    CLIMESSAGE,
    CONNECTION
  }

  /**
   * Check to see if Android is connected to a Neosensory device
   *
   * @return True if connected. False otherwise.
   */
  public boolean getNeoDeviceConnected() {
    return neoDeviceConnected;
  }

  /**
   * Check to see if a command line interface (CLI) is ready and available to accept commands
   *
   * @return True if the CLI is ready and available. False otherwise.
   */
  public boolean getNeoCliReady() {
    return neoCliReady;
  }

  /**
   * Get last obtained response from the command line interface (CLI)
   *
   * @return last obtained response as a String.
   */
  public String getNeoCliResponse() {
    return neoCliResponse;
  }

  // sendCommand encodes the command strings for the CLI in the proper format.
  private boolean sendCommand(String CliCommand) {
    if ((neoDeviceConnected) && (neoCliReady)) {
      byte[] CliBytes = CliCommand.getBytes(StandardCharsets.UTF_8);
      neoPeripheral.writeCharacteristic(neoWriteCharacteristic, CliBytes, WriteType.WITH_RESPONSE);
      return true;
    } else {
      return false;
    }
  }

  // TODO: Create sync + async modes for awaiting CLI feedback

  /**
   * Pause the running algorithm on the device to accept motor control over the CLI. This command
   * requires successful * developer authorization, otherwise, the command will fail. TODO: handle
   * returning JSON responses from device
   */
  public void pauseDeviceAlgorithm() {
    stopAudio();
    enableMotors();
  }

  /**
   * (Re)starts the device’s microphone audio acquisition / algorithm. This command requires
   * successful developer authorization, otherwise, the command will fail. This is functionally the
   * same as startAudio();
   */
  public void resumeDeviceAlgorithm() {
    sendCommand("audio start\n");
  }

  /**
   * Request developer authorization. The CLI returns the message “Please type 'accept' and hit
   * enter to agree to Neosensory Inc's Developer Terms and Conditions, which can be viewed at
   * https://neosensory.com/legal/dev-terms-service
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean sendDeveloperAPIAuth() {
    return sendCommand("auth as developer\n");
  }

  /**
   * After successfully calling auth as developer, use the accept command to agree to the Neosensory
   * Developer API License (https://neosensory.com/legal/dev-terms-service/). Successfully calling
   * this unlocks the following commands: audio start, audio stop, motors_clear_queue, motors start,
   * motors_stop, motors vibrate.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean acceptApiTerms() {
    return sendCommand("accept\n");
  }

  /**
   * (Re)starts the device’s microphone audio acquisition. This command requires successful
   * developer authorization, otherwise, the command will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean startAudio() {
    return sendCommand("audio start\n");
  }

  /**
   * Stop the device’s microphone audio acquisition. This should be called prior to transmitting
   * motor vibration data. This command requires successful developer authorization, otherwise, the
   * command will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean stopAudio() {
    sendCommand("audio stop\n");
    return clearMotorQueue(); // firmware currently requires clearing the motor queue after this
  }

  /**
   * Obtain the device’s battery level in %. This command does not require developer authorization
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean getBatteryLevel() {
    return sendCommand("device battery_soc\n");
  }

  /**
   * Obtain various device and firmware information. This command does not require developer
   * authorization.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean getDeviceInfo() {
    return sendCommand("device info\n");
  }

  /**
   * Clear any vibration commands sitting the device’s motor FIFO queue. This should be called prior
   * to streaming control frames using motors vibrate. This command requires successful developer
   * authorization, otherwise, the command will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean clearMotorQueue() {
    return sendCommand("motors clear_queue\n");
  }

  /**
   * Initialize and start the motors interface. The motors can then accept motors vibrate commands.
   * This command requires successful developer authorization, otherwise, the command will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean enableMotors() {
    return sendCommand("motors start\n");
  }

  /**
   * Clear the motors command queue and shut down the motor drivers. This command requires
   * successful developer authorization, otherwise, the command will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean disableMotors() {
    return sendCommand("motors stop\n");
  }

  /**
   * Send a frame that turns off the motors. Note the API CLI command "motors stop" disables the
   * motor drivers. This command requires successful developer authorization, otherwise, the command
   * will fail.
   *
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean stopMotors() {
    return vibrateMotors(new int[4]);
  }

  /**
   * Set the actuators amplitudes on a connected Neosensory device. Note: actuators will stay
   * vibrating indefinitely on the last frame received until a new control * frame is received
   *
   * @param motorValues byte array of length # of motors of the target device (e.g. should be 4 if a
   *     Neosensory Buzz). Element values should between 0 (motor off) and 255 (motor at full
   *     amplitude). Example input format: new byte[] {(byte) 155, (byte) 0, (byte) 0, (byte) 0};
   * @return true if connected to a valid device that is ready to accept CLI commands. TODO: handle
   *     returning JSON response from the device
   */
  public boolean vibrateMotors(int[] motorValues) {
    // unfortunately, bytes are signed in java and our input values are on [0, 255]
    byte[] motorValuesBytes = new byte[motorValues.length];
    for (int i = 0; i < motorValues.length; i++) {
      motorValuesBytes[i] = (byte) (motorValues[i]);
    }
    byte[] b64motorValues = Base64.getEncoder().encode(motorValuesBytes);
    String fireCommand =
        "motors vibrate " + new String(b64motorValues, StandardCharsets.UTF_8) + "\n";
    return sendCommand(fireCommand);
  }

  /** If connected to a Neosensory device, disconnect it */
  public void disconnectNeoDevice() {
    if ((neoDeviceConnected) && (neoPeripheral != null)) {
      central.cancelConnection(neoPeripheral);
    }
  }

  /** Attempt to reconnect to a Neosensory device if disconnected */
  public void attemptNeoReconnect() {
    if ((!neoDeviceConnected) && (neoPeripheral != null)) {
      handler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              central.autoConnectPeripheral(neoPeripheral, peripheralCallback);
            }
          },
          5000);
    }
  }

  // Callback for peripherals
  private final BluetoothPeripheralCallback peripheralCallback =
      new BluetoothPeripheralCallback() {

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onServicesDiscovered(BluetoothPeripheral peripheral) {
          // Attempt to turn on notifications from the UART
          if (peripheral.getService(UART_OVER_BLE_SERVICE_UUID) != null) {
            BluetoothGattCharacteristic bleNotifyCharacteristic =
                peripheral.getCharacteristic(UART_OVER_BLE_SERVICE_UUID, UART_TX_NOTIFY_UUID);
            peripheral.setNotify(bleNotifyCharacteristic, true);
            neoPeripheral = peripheral;
            neoWriteCharacteristic =
                peripheral.getCharacteristic(UART_OVER_BLE_SERVICE_UUID, UART_RX_WRITE_UUID);
            neoCliReady = true;
            broadcast(StatusUpdateType.CLIREADINESS,neoCliReady);
            Log.i(TAG, "SUCCESS: CLI ready to accept commands");

          } else {
            neoCliReady = false;
            broadcast(StatusUpdateType.CLIREADINESS,neoCliReady);
            Log.i(TAG, "Failure: No services found on UUID");
          }
        }

        // Log a successful change in notification status for the characteristic
        @Override
        public void onNotificationStateUpdate(
                BluetoothPeripheral peripheral,
                BluetoothGattCharacteristic characteristic,
                GattStatus status) {
          if (status == GattStatus.SUCCESS) {
            if (peripheral.isNotifying(characteristic)) {
              Log.i(
                  TAG,
                  String.format("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid()));
            } else {
              Log.i(
                  TAG,
                  String.format("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid()));
            }
          } else {
            Log.e(
                TAG,
                String.format(
                    "ERROR: Changing notification state failed for %s", characteristic.getUuid()));
          }
        }

        // Log pass/fail upon attempting a a write characteristic
        public void onCharacteristicWrite(
                BluetoothPeripheral peripheral,
                byte[] value,
                BluetoothGattCharacteristic characteristic,
                GattStatus status) {
          if (status == GattStatus.SUCCESS) {
            Log.i(
                TAG,
                String.format(
                    "SUCCESS: Writing <%s> to <%s>",
                    bytes2String(value), characteristic.getUuid().toString()));
          } else {
            Log.i(
                TAG,
                String.format(
                    "ERROR: Failed writing <%s> to <%s>",
                    bytes2String(value), characteristic.getUuid().toString()));
          }
        }

        // For now we'll only broadcast UART_TX Notifications (i.e. CLI Output) in our module and
        // send other notifications to logcat
        @Override
        public void onCharacteristicUpdate(
                BluetoothPeripheral peripheral,
                byte[] value,
                BluetoothGattCharacteristic characteristic,
                GattStatus status) {
          if (status != status.SUCCESS) return;
          UUID characteristicUUID = characteristic.getUuid();
          BluetoothBytesParser parser = new BluetoothBytesParser(value);
          if (characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
            String manufacturer = parser.getStringValue(0);
            Log.i(TAG, String.format("Received manufacturer: %s", manufacturer));
          } else if (characteristicUUID.equals(UART_TX_NOTIFY_UUID)) {
            neoCliResponse = parser.getStringValue(0);
            Log.i(TAG, String.format("Received notification: %s", neoCliResponse));
            broadcast(StatusUpdateType.CLIMESSAGE, neoCliResponse);
          } else if (characteristicUUID.equals(UART_RX_WRITE_UUID)) {
            String rx_write_val = parser.getStringValue(0);
            Log.i(TAG, String.format("Received rxwrite: %s", rx_write_val));
          }
        }
      };

  private void broadcast(StatusUpdateType updateType, String message) {
    Intent intent = new Intent("BlessedBroadcast");
    switch (updateType) {
      case CLIMESSAGE:
        intent.putExtra("com.neosensory.neosensoryblessed.CliMessage", message);
        context.sendBroadcast(intent);
        break;
      default:
        break;
    }
  }

  private void broadcast(StatusUpdateType updateType, Boolean state) {
    Intent intent = new Intent("BlessedBroadcast");
    switch (updateType) {
      case CONNECTION:
        intent.putExtra("com.neosensory.neosensoryblessed.ConnectedState", state);
        context.sendBroadcast(intent);
        break;
      case CLIREADINESS:
        intent.putExtra("com.neosensory.neosensoryblessed.CliReadiness", state);
        context.sendBroadcast(intent);
        break;
      default:
        break;
    }
  }

  // Callbacks for processing Bluetooth state changes
  private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback =
          new BluetoothCentralManagerCallback() {
        // Upon connecting to a peripheral, log the output and  broadcast message (e.g. to Main
        // Activity)
        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
          Log.i(TAG, String.format("connected to '%s'", peripheral.getName()));
          neoDeviceConnected = true;
          broadcast(StatusUpdateType.CONNECTION,neoDeviceConnected);
        }

        // Upon a failed connection, log the output
        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
          neoDeviceConnected = false;
          broadcast(StatusUpdateType.CONNECTION,neoDeviceConnected);
          neoCliReady = false;
          broadcast(StatusUpdateType.CLIREADINESS,neoCliReady);
          Log.e(
              TAG,
              String.format("connection '%s' failed with status %d", peripheral.getName(), status));
        }

        // Upon a disconnect, log the output and attempt to reconnect every 5 seconds.
        @Override
        public void onDisconnectedPeripheral(
                final BluetoothPeripheral peripheral, HciStatus status) {
          neoDeviceConnected = false;
          broadcast(StatusUpdateType.CONNECTION,neoDeviceConnected);
          neoCliReady = false;
          broadcast(StatusUpdateType.CLIREADINESS,neoCliReady);

          Log.i(
                  TAG, String.format("disconnected '%s' with status %s", peripheral.getName(), status.name()));
          if (autoReconnectEnabled) {
            if (!neoDeviceConnected) {
              handler.postDelayed(
                  new Runnable() {
                    @Override
                    public void run() {
                      central.autoConnectPeripheral(peripheral, peripheralCallback);
                    }
                  },
                  5000);
            }
          }
        }

        // Upon discovering target peripheral, stop scan and initiate connection.
        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
          Log.i(TAG, String.format("Found peripheral '%s'", peripheral.getName()));
          central.stopScan();
          central.connectPeripheral(peripheral, peripheralCallback);
        }
      };

  /**
   * Request the Activity enable Bluetooth
   *
   * @param activity the Activity trying to call this. Typically you would pass in the variable
   *     `this` from the Activity.
   */
  public static void requestBluetoothOn(Activity activity) {
    // Make sure Bluetooth is supported and has the needed permissions
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) return;
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
  }

  /**
   * Create and return instance using constructor used to connect to first discovered device
   * containing the name "Buzz" NOTE: There should only exist one instance of NeosensoryBlessed at a
   * time. If you try to create a new instance, the parameters will be ignored and you'll get the
   * previously created instance.
   *
   * @param context the Android Context * @param[in] autoReconnect boolean for if the Bluetooth
   *     handler should automatically attempt to * reconnect to the device if a connection is lost.
   * @param neoNames a list of Strings for finding a potential device to connect to by name. For
   *     example, if given just the entry {"Buzz"}, the module will attempt to connect to the first
   *     device found containing the "Buzz" in the name.
   * @param autoReconnect boolean for if the Bluetooth handler should automatically attempt to *
   *     reconnect to the device if a connection is lost.
   * @return the instance of the NeosensoryBlessed object
   */
  public static synchronized NeosensoryBlessed getInstance(
      Context context, String[] neoNames, boolean autoReconnect) {
    if (instance == null) {
      instance = new NeosensoryBlessed(context.getApplicationContext(), neoNames, autoReconnect);
    }
    return instance;
  }

  /**
   * Create and return instance using constructor used to connect to a device with a specific
   * address e.g. "EB:CA:85:38:19:1D" context the Android Context. NOTE: There should only exist one
   * instance of NeosensoryBlessed at a * time. If you try to create a new instance, the parameters
   * will be ignored and you'll get the * previously created instance.
   *
   * @param neoAddress string in the format of a desired address e.g. "EB:CA:85:38:19:1D"
   * @param autoReconnect boolean for if the Bluetooth handler should automatically attempt to
   *     reconnect to the device if a connection is lost.
   * @return the instance of the NeosensoryBlessed object
   */
  public static synchronized NeosensoryBlessed getInstance(
      Context context, String neoAddress, boolean autoReconnect) {
    if (instance == null) {
      instance = new NeosensoryBlessed(context.getApplicationContext(), neoAddress, autoReconnect);
    }
    return instance;
  }

  /**
   * Constructor used to connect to a device with a specific address e.g. "EB:CA:85:38:19:1D"
   *
   * @param context the Android Context
   * @param neoAddress string in the format of a desired address e.g. "EB:CA:85:38:19:1D"
   * @param autoReconnect boolean for if the Bluetooth handler should automatically attempt to
   *     reconnect to the device if a connection is lost.
   */
  private NeosensoryBlessed(Context context, String neoAddress, boolean autoReconnect) {
    this.context = context;
    autoReconnectEnabled = autoReconnect;
    // Create BluetoothCentral
    central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());

    // Scan for peripherals with a certain service UUIDs
    central.startPairingPopupHack();
    central.scanForPeripheralsWithAddresses(new String[] {neoAddress});
  }

  /**
   * Constructor used to connect to first discovered device containing the name "Buzz"
   *
   * @param context the Android Context * @param[in] autoReconnect boolean for if the Bluetooth
   *     handler should automatically attempt to * reconnect to the device if a connection is lost.
   * @param neoNames a list of Strings for finding a potential device to connect to by name. For
   *     example, if given just the entry {"Buzz"}, the module will attempt to connect to the first
   *     device found containing "Buzz" in the name.
   * @param autoReconnect boolean for if the Bluetooth handler should automatically attempt to
   *     reconnect to the device if a connection is lost.
   */
  private NeosensoryBlessed(Context context, String[] neoNames, boolean autoReconnect) {
    this.context = context;
    autoReconnectEnabled = autoReconnect;
    // Create BluetoothCentral
    central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());
    // Scan for peripherals with a certain service UUIDs
    central.startPairingPopupHack();
    central.scanForPeripheralsWithNames(neoNames);
  }
}
