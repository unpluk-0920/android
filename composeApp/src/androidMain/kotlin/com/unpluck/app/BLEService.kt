package com.unpluck.app

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
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

// These UUIDs must match your ESP32 code
private const val SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
private const val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

class BleService : Service() {

    private val TAG = "BleService"
    private val NOTIFICATION_CHANNEL_ID = "BleServiceChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    private var lastTriggerTimestamp: Long = 0

    companion object {
        const val ACTION_CLOSE_SPACE_ACTIVITY = "com.unpluck.app.ACTION_CLOSE_SPACE_ACTIVITY"
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
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
        if (isScanning || !bluetoothAdapter.isEnabled) return
        Log.d(TAG, "Starting BLE scan")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid.fromString(SERVICE_UUID))
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
            super.onScanResult(callbackType, result)
            Log.d(TAG, "Device found: ${result.device.name} at ${result.device.address}")
            bleScanner.stopScan(this)
            isScanning = false
            result.device.connectGatt(applicationContext, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(TAG, "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    gatt.discoverServices()
                    updateNotification("Connected to ESP32")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    bluetoothGatt = null
                    startBleScan() // Reconnect
                    updateNotification("Disconnected. Searching...")
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
                bluetoothGatt = null
                startBleScan() // Reconnect
                updateNotification("Connection failed. Searching...")
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
        if (currentTime - lastTriggerTimestamp < 1000) { // 1000ms = 1 second
            Log.d(TAG, "Trigger ignored (debounce)")
            return // Exit the function if it's too soon
        }
        lastTriggerTimestamp = currentTime // Update the timestamp
        when (message) {
            "LED is ON" -> {
                Log.d(TAG, "Trigger message received: Launching SpaceActivity")
                val intent = Intent(this, SpaceActivity::class.java).apply {
                    // This flag is crucial for starting an activity from a background service.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "LED is OFF" -> {
                Log.d(TAG, "LED is OFF: Closing SpaceActivity Sending broadcast")
                val intent = Intent(ACTION_CLOSE_SPACE_ACTIVITY).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }
    // --- END OF MODIFICATION ---

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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        bleScanner.stopScan(scanCallback)
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onBind(intent: Intent): IBinder? = null
}