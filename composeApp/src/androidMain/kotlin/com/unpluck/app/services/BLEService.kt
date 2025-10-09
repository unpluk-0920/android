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
import com.unpluck.app.defs.CONSTANTS

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
        const val ACTION_START_SCAN = "com.unpluck.app.ACTION_START_SCAN"
        const val ACTION_CONNECT = "com.unpluck.app.ACTION_CONNECT"
        const val ACTION_DEVICE_FOUND = "com.unpluck.app.ACTION_DEVICE_FOUND"
        const val ACTION_STATUS_UPDATE = "com.unpluck.app.ACTION_STATUS_UPDATE"

        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        const val EXTRA_STATUS_MESSAGE = "EXTRA_STATUS_MESSAGE"
        const val EXTRA_IS_CONNECTED = "EXTRA_IS_CONNECTED"
        var isServiceRunning = false
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "BleService onCreate")

        // --- MOVE THE ONE-TIME SETUP LOGIC HERE ---
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification("Unpluk service is running."))

        // Auto-connect logic
        val prefs = getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
        val savedAddress = prefs.getString("SAVED_BLE_DEVICE_ADDRESS", null)
        if (savedAddress != null) {
            broadcastStatus("Attempting to auto-connect...", false)
            val device = bluetoothAdapter.getRemoteDevice(savedAddress)
            connectToDevice(device)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SCAN -> {
                startBleScan()
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    val device = bluetoothAdapter.getRemoteDevice(address)
                    connectToDevice(device)
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        Log.d(TAG, "Attempting to start BLE scan...")
        if (!hasPermissions() || isScanning) return

        broadcastStatus("Scanning for devices...", false)
        // updateNotification("Scanning for devices...")
        isScanning = true
        // REMOVED: ScanFilter, we now scan for everything
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner.startScan(null, scanSettings, scanCallback)

        // Stop scan after 10 seconds to save battery
        Handler(Looper.getMainLooper()).postDelayed({
            if(isScanning) {
                isScanning = false
                bleScanner.stopScan(scanCallback)
                broadcastStatus("Scan finished.", false)
                // updateNotification("Scan finished.")
            }
        }, 10000)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            Log.d(TAG, "Scanner saw a device: Name=${result.device.name}, Address=${result.device.address}")

            val deviceName = result.device.name
            if (deviceName != null) {
                val intent = Intent(ACTION_DEVICE_FOUND).apply {
                    putExtra(EXTRA_DEVICE_ADDRESS, result.device.address)
                    putExtra(EXTRA_DEVICE_NAME, deviceName)
                    setPackage(packageName) // <-- ADD THIS LINE
                }
                sendBroadcast(intent)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        disconnect()

        if (isScanning) {
            isScanning = false
            bleScanner.stopScan(scanCallback)
            Log.d(TAG, "Scan stopped due to connection attempt.")
            // Also update the notification to stop showing "Scanning"
            // updateNotification("Connecting...")
        }

        connectionAttemptRunnable?.let { timeoutHandler.removeCallbacks(it) }

        broadcastStatus("Connecting to ${device.name ?: device.address}...", false)
        // updateNotification("Connecting to ${device.name ?: "device"}...")
        // --- NEW: Start a connection timeout ---
        connectionAttemptRunnable = Runnable {
            Log.e(TAG, "Connection timed out.")
            disconnect() // Clean up everything
            broadcastStatus("Connection timed out.", false)
            startBleScan() // Try scanning again
        }
        timeoutHandler.postDelayed(connectionAttemptRunnable!!, CONNECTION_TIMEOUT_MS)

        // Connect
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // First, always remove the timeout callback as a connection attempt has completed
            connectionAttemptRunnable?.let { timeoutHandler.removeCallbacks(it) }
            connectionAttemptRunnable = null

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Successfully connected to ${gatt.device.address}")
                    broadcastStatus("Connected!", true)
                    val prefs = getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
                    prefs.edit { putString("SAVED_BLE_DEVICE_ADDRESS", gatt.device.address) }
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.discoverServices()
                    }, 600)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Disconnected from ${gatt.device.address}")
                    disconnect() // Use our central cleanup function
                    broadcastStatus("Disconnected.", false)
                    startBleScan() // Optional: try to find it again
                }
            } else {
                Log.w(TAG, "Connection attempt failed with status: $status")
                disconnect() // Use our central cleanup function
                broadcastStatus("Connection failed.", false)
                startBleScan()
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

    private fun broadcastStatus(message: String, isConnected: Boolean) {
        Log.d(TAG, "Broadcasting Status: '$message', isConnected: $isConnected")
        updateNotification(message)
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_STATUS_MESSAGE, message)
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
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

        prefs.edit { putString(CONSTANTS.KEY_APP_MODE, newMode) }
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

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        isServiceRunning = false
    }

    private fun disconnect() {
        Log.d(TAG, "Disconnecting and cleaning up resources.")
        // Cancel any pending connection timeout
        connectionAttemptRunnable?.let { timeoutHandler.removeCallbacks(it) }
        connectionAttemptRunnable = null

        // Disconnect and close the GATT connection
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onBind(intent: Intent): IBinder? = null
}