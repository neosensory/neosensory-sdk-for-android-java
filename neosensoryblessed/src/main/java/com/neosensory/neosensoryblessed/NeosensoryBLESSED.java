package com.neosensory.neosensoryblessed;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothCentralCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;
import static com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS;


public class NeosensoryBLESSED {

    private final String TAG = NeosensoryBLESSED.class.getSimpleName();

    // UUIDs for Neosensory UART over BLE
    private static final UUID UART_OVER_BLE_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX_WRITE_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX_NOTIFY_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // UUIDs for the Device Information service (DIS)
    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");


    // Local variables
    private BluetoothCentral central;
    private static NeosensoryBLESSED instance = null;
    private Context context;
    private Handler handler = new Handler();
    private int currentTimeCounter = 0;

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {


        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onServicesDiscovered(BluetoothPeripheral peripheral) {
            Log.i(TAG, "discovered services");

            // Read manufacturer and model number from the Device Information Service
            if (peripheral.getService(DIS_SERVICE_UUID) != null) {
                peripheral.readCharacteristic(peripheral.getCharacteristic(DIS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID));
            }

            // Turn on notifications from the UART
            if (peripheral.getService(UART_OVER_BLE_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic bleNotifyCharacteristic = peripheral.getCharacteristic(UART_OVER_BLE_SERVICE_UUID, UART_TX_NOTIFY_UUID);
                peripheral.setNotify(bleNotifyCharacteristic, true);
            }
            // Authorize, turn off audio, start motors, send test sequence
            if (peripheral.getService(UART_OVER_BLE_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic bleWriteCharacteristic = peripheral.getCharacteristic(UART_OVER_BLE_SERVICE_UUID, UART_RX_WRITE_UUID);
                if ((bleWriteCharacteristic.getProperties() & PROPERTY_WRITE) > 0) {

                    String auth = "auth as developer\n";
                    byte[] bauth = auth.getBytes(StandardCharsets.UTF_8);
                    peripheral.writeCharacteristic(bleWriteCharacteristic, bauth, WRITE_TYPE_DEFAULT);

                    String accept = "accept\n";
                    byte[] baccept = accept.getBytes(StandardCharsets.UTF_8);
                    peripheral.writeCharacteristic(bleWriteCharacteristic, baccept, WRITE_TYPE_DEFAULT);

                    String audstop = "audio stop\n";
                    byte[] baudstop = audstop.getBytes(StandardCharsets.UTF_8);
                    peripheral.writeCharacteristic(bleWriteCharacteristic, baudstop, WRITE_TYPE_DEFAULT);

                    String mstart = "motors start\n";
                    byte[] bmstart = mstart.getBytes(StandardCharsets.UTF_8);
                    peripheral.writeCharacteristic(bleWriteCharacteristic, bmstart, WRITE_TYPE_DEFAULT);

                    byte[] ex_motor_frame_array = new byte[]{(byte)155,(byte)0,(byte)0,(byte)0};
                    byte[] b64array = Base64.getEncoder().encode(ex_motor_frame_array);
                    String motor_test_sequence = new String(b64array, StandardCharsets.UTF_8);
                    String fire_test_sequence = "motors vibrate " + motor_test_sequence + "\n";

                    byte[] fts = fire_test_sequence.getBytes(StandardCharsets.UTF_8);
                    peripheral.writeCharacteristic(bleWriteCharacteristic, fts, WRITE_TYPE_DEFAULT);

                    // Define a thread that continuously updates the motors.
                    class RunnableBLE implements Runnable {
                        BluetoothPeripheral thread_peripheral;
                        BluetoothGattCharacteristic thread_bleWriteCharacteristic;
                        public RunnableBLE(BluetoothPeripheral peripheral) {
                            thread_peripheral = peripheral;
                            thread_bleWriteCharacteristic = thread_peripheral.getCharacteristic(UART_OVER_BLE_SERVICE_UUID, UART_RX_WRITE_UUID);
                        }

                        public void run() {
                            // loop until the thread is interrupted
                            int motor_id = 0;
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    Thread.sleep(2000);
                                    motor_id = (motor_id+1)%4;
                                    byte[] mfa = new byte[]{(byte)0,(byte)0,(byte)0,(byte)0};
                                    mfa[motor_id] = (byte)255;
                                    byte[] mfa2b64array = Base64.getEncoder().encode(mfa);
                                    String mfa_sequence = new String(mfa2b64array, StandardCharsets.UTF_8);
                                    String fire_mfa_sequence = "motors vibrate " + mfa_sequence + "\r\n";
                                    byte[] fmfas = fire_mfa_sequence.getBytes(StandardCharsets.UTF_8);
                                    thread_peripheral.writeCharacteristic(thread_bleWriteCharacteristic, fmfas, WRITE_TYPE_DEFAULT);
                                    Log.i(TAG, String.format("Thread launch+Sleep"));
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    // let's launch a thread that cycles through the actuators...
                    // Runnable r = new RunnableBLE(peripheral);
                    // new Thread(r).start();

                }
            }
        }


        @Override
        public void onNotificationStateUpdate(BluetoothPeripheral peripheral, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                if(peripheral.isNotifying(characteristic)) {
                    Log.i(TAG, String.format("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid()));
                } else {
                    Log.i(TAG, String.format("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid()));
                }
            } else {
                Log.e(TAG, String.format("ERROR: Changing notification state failed for %s", characteristic.getUuid()));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                Log.i(TAG, String.format("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString()));
            } else {
                Log.i(TAG, String.format("ERROR: Failed writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString()));
            }
        }

        @Override
        public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, int status) {
            if(status != GATT_SUCCESS) return;
            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if(characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Log.i(TAG, String.format("Received manufacturer: %s", manufacturer));
            }
            else if(characteristicUUID.equals(UART_TX_NOTIFY_UUID)){
                String notification_val = parser.getStringValue(0);
                Log.i(TAG, String.format("Received notification: %s", notification_val));

                Intent intent = new Intent("CLIOutput");
                intent.putExtra("notification_value", notification_val);
                context.sendBroadcast(intent);
            }
            else if(characteristicUUID.equals(UART_RX_WRITE_UUID)){
                String rx_write_val = parser.getStringValue(0);
                Log.i(TAG, String.format("Received rxwrite: %s", rx_write_val));
            }
        }
    };

    private void broadcast_connected(Boolean connected_state) {
        Intent intent = new Intent("ConnectionState");
        intent.putExtra("connected_state", connected_state);
        context.sendBroadcast(intent);
    }

    // Callback for central
    private final BluetoothCentralCallback bluetoothCentralCallback = new BluetoothCentralCallback() {

        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Log.i(TAG, String.format("connected to '%s'", peripheral.getName()));
            broadcast_connected(true);
        }

        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, final int status) {
            Log.e(TAG, String.format("connection '%s' failed with status %d", peripheral.getName(), status ));
        }

        @Override
        public void onDisconnectedPeripheral(final BluetoothPeripheral peripheral, final int status) {
            Log.i(TAG, String.format("disconnected '%s' with status %d", peripheral.getName(), status));

            // Reconnect to this device when it becomes available again
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    central.autoConnectPeripheral(peripheral, peripheralCallback);
                }
            }, 5000);
        }

        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            Log.i(TAG, String.format("Found peripheral '%s'", peripheral.getName()));
            central.stopScan();
            central.connectPeripheral(peripheral, peripheralCallback);
        }
    };

    public static synchronized NeosensoryBLESSED getInstance(Context context) {
        if (instance == null) {
            instance = new NeosensoryBLESSED(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized NeosensoryBLESSED getInstance(Context context, String BuzzAddress) {
        if (instance == null) {
            instance = new NeosensoryBLESSED(context.getApplicationContext(), BuzzAddress);
        }
        return instance;
    }
    
    private NeosensoryBLESSED(Context context, String NeoAddress) {
        this.context = context;
        // Create BluetoothCentral
        central = new BluetoothCentral(context, bluetoothCentralCallback, new Handler());
        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();
        //central.scanForPeripheralsWithAddresses(new String[]{"EB:CA:85:38:19:1D"});
        central.scanForPeripheralsWithAddresses(new String[]{NeoAddress});
    }

    private NeosensoryBLESSED(Context context) {
        this.context = context;
        // Create BluetoothCentral
        central = new BluetoothCentral(context, bluetoothCentralCallback, new Handler());
        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();
        central.scanForPeripheralsWithNames(new String[]{"Buzz"});
    }



}
