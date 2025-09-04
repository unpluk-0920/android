package com.unpluck.app.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.unpluck.app.AppMode
import com.unpluck.app.MainActivity
import com.unpluck.app.R
import java.util.*
import androidx.core.content.edit

// These UUIDs must match your ESP32 code
private const val SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
private const val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

@SuppressLint("MissingPermission")
class BleService : Service() {

    private val TAG = "BleService"
    private val NOTIFICATION_CHANNEL_ID = "BleServiceChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1

    private val CONNECTION_TIMEOUT_MS = 15000L // 15 seconds


    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    private var lastTriggerTimestamp: Long = 0

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var connectionAttemptRunnable: Runnable? = null

    companion object {
        const val ACTION_CLOSE_SPACE_ACTIVITY = "com.unpluck.app.ACTION_CLOSE_SPACE_ACTIVITY"
        const val ACTION_ENTER_FOCUS_MODE = "com.unpluck.app.ACTION_ENTER_FOCUS_MODE"
        const val ACTION_EXIT_FOCUS_MODE = "com.unpluck.app.ACTION_EXIT_FOCUS_MODE"
        var isServiceRunning = false
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        createNotificationChannel()
        val notification = createForegroundNotification("Searching for ESP32...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
        startBleScan()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasPermissions() || isScanning) return
        Log.d(TAG, "Starting BLE scan")
        updateNotification("Searching for smart case...")
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        isScanning = true
        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isScanning) return // --- NEW: Ignore results if we're no longer scanning
            Log.d(TAG, "Device found: ${result.device.name ?: "Unnamed"} at ${result.device.address}")

            // --- NEW: Stop scanning immediately and set flag ---
            isScanning = false
            bleScanner.stopScan(this)

            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        updateNotification("Connecting to smart case...")
        // --- NEW: Start a connection timeout ---
        connectionAttemptRunnable = Runnable {
            Log.e(TAG, "Connection timed out. Disconnecting and restarting scan.")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            startBleScan()
        }
        timeoutHandler.postDelayed(connectionAttemptRunnable!!, CONNECTION_TIMEOUT_MS)

        // Connect
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            connectionAttemptRunnable?.let { timeoutHandler.removeCallbacks(it) }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Successfully connected to ${gatt.device.address}")
                    bluetoothGatt = gatt
                    // --- ADD THIS DELAY ---
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.discoverServices()
                    }, 600)
                    updateNotification("Connected to smart case")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Disconnected from ${gatt.device.address}")
                    gatt.close()
                    bluetoothGatt = null
                    startBleScan() // Reconnect
                }
            } else {
                Log.e(TAG, "Connection failed with status: $status")
                gatt.close()
                bluetoothGatt = null
                startBleScan() // Reconnect
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                if (characteristic != null) {
                    enableNotifications(gatt, characteristic)
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val cccdUuid = UUID.fromString(CCCD_UUID)
            val descriptor = characteristic.getDescriptor(cccdUuid)
            gatt.setCharacteristicNotification(characteristic, true)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val message = characteristic.value.toString(Charsets.UTF_8)
            Log.i(TAG, "Received notification: $message")
            handleBleMessage(message)
        }
    }

    private fun handleBleMessage(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTimestamp < 1000) { // Debounce
            Log.d(TAG, "Trigger ignored (debounce)")
            return
        }
        lastTriggerTimestamp = currentTime

        // 1. Get SharedPreferences
        val prefs = getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)

        // 2. Determine the new mode and save it
        val newMode = if (message.contains("ON")) {
            AppMode.FOCUS_MODE.name // Use the enum name as a string
        } else if (message.contains("OFF")) {
            AppMode.NORMAL_MODE.name // We will rename this enum value later
        } else {
            return // Do nothing if the message is unknown
        }

        prefs.edit { putString("APP_MODE_KEY", newMode) }
        Log.d(TAG, "App mode saved: $newMode")

        // 3. Send a single, generic broadcast that the mode has changed
        val intent = Intent("com.unpluck.app.ACTION_MODE_CHANGED").setPackage(packageName)
        sendBroadcast(intent)

        // If we are entering focus mode, we MUST bring our launcher to the front.
        if (newMode == AppMode.FOCUS_MODE.name) {
            Log.d(TAG, "Bringing MainActivity to the foreground for Focus Mode.")
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                // This flag is essential for starting an activity from a background service.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(mainActivityIntent)
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "BLE Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createForegroundNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ESP32 Connector")
            .setContentText(text)
            // Use the app's launcher icon instead of a missing one
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createForegroundNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        bleScanner.stopScan(scanCallback)
        bluetoothGatt?.close()
        bluetoothGatt = null
        isServiceRunning = false

    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
    }

    override fun onBind(intent: Intent): IBinder? = null
}