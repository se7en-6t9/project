package com.bridgekvm.hid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.Executors

class BluetoothHidModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val executor = Executors.newSingleThreadExecutor()

  private val bluetoothManager: BluetoothManager? =
    reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
  private var hidDevice: BluetoothHidDevice? = null
  private var connectedDevice: BluetoothDevice? = null

  override fun getName(): String = "BluetoothHidModule"

  private val profileCallback = object : BluetoothProfile.ServiceListener {
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
      if (profile == BluetoothProfile.HID_DEVICE) {
        hidDevice = proxy as BluetoothHidDevice
        registerApp()
      }
    }

    override fun onServiceDisconnected(profile: Int) {
      if (profile == BluetoothProfile.HID_DEVICE) {
        hidDevice = null
      }
    }
  }

  private val hidCallback = object : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
      if (registered) {
        Log.d(TAG, "HID app registered")
      } else {
        Log.w(TAG, "HID app unregistered")
      }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
      connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
      emitConnectionState(state)
    }
  }

  @ReactMethod
  fun initialize(promise: Promise) {
    if (bluetoothAdapter == null) {
      promise.reject("NO_BLUETOOTH", "Bluetooth is not supported")
      return
    }
    if (!bluetoothAdapter.isEnabled) {
      promise.reject("BT_DISABLED", "Bluetooth is disabled")
      return
    }
    val ok = bluetoothAdapter.getProfileProxy(
      reactContext,
      profileCallback,
      BluetoothProfile.HID_DEVICE
    )
    if (!ok) {
      promise.reject("HID_PROXY", "Failed to get HID device profile")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun connect(deviceAddress: String, promise: Promise) {
    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
    if (device == null) {
      promise.reject("NO_DEVICE", "Invalid device address")
      return
    }
    val success = hidDevice?.connect(device) ?: false
    if (!success) {
      promise.reject("CONNECT_FAILED", "Failed to connect to device")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun disconnect(promise: Promise) {
    val device = connectedDevice
    if (device == null) {
      promise.resolve(true)
      return
    }
    val success = hidDevice?.disconnect(device) ?: false
    if (!success) {
      promise.reject("DISCONNECT_FAILED", "Failed to disconnect")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun sendKeyReport(modifier: Int, keyCode: Int, promise: Promise) {
    val device = connectedDevice
    if (device == null) {
      promise.reject("NOT_CONNECTED", "No connected device")
      return
    }
    val report = byteArrayOf(
      modifier.toByte(),
      0x00,
      keyCode.toByte(),
      0x00, 0x00, 0x00, 0x00, 0x00
    )
    val ok = hidDevice?.sendReport(device, REPORT_ID_KEYBOARD, report) ?: false
    if (!ok) {
      promise.reject("SEND_FAILED", "Failed to send key report")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun releaseKeys(promise: Promise) {
    val device = connectedDevice
    if (device == null) {
      promise.reject("NOT_CONNECTED", "No connected device")
      return
    }
    val report = ByteArray(8)
    val ok = hidDevice?.sendReport(device, REPORT_ID_KEYBOARD, report) ?: false
    if (!ok) {
      promise.reject("SEND_FAILED", "Failed to send key release")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun sendMouseReport(buttonMask: Int, deltaX: Int, deltaY: Int, wheel: Int, promise: Promise) {
    val device = connectedDevice
    if (device == null) {
      promise.reject("NOT_CONNECTED", "No connected device")
      return
    }
    val report = byteArrayOf(
      buttonMask.toByte(),
      deltaX.toByte(),
      deltaY.toByte(),
      wheel.toByte()
    )
    val ok = hidDevice?.sendReport(device, REPORT_ID_MOUSE_REL, report) ?: false
    if (!ok) {
      promise.reject("SEND_FAILED", "Failed to send mouse report")
      return
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun sendMouseAbsoluteReport(
    buttonMask: Int,
    x: Int,
    y: Int,
    wheel: Int,
    promise: Promise
  ) {
    val device = connectedDevice
    if (device == null) {
      promise.reject("NOT_CONNECTED", "No connected device")
      return
    }
    val clampedX = x.coerceIn(0, ABS_MAX)
    val clampedY = y.coerceIn(0, ABS_MAX)
    val report = byteArrayOf(
      buttonMask.toByte(),
      (clampedX and 0xFF).toByte(),
      ((clampedX shr 8) and 0xFF).toByte(),
      (clampedY and 0xFF).toByte(),
      ((clampedY shr 8) and 0xFF).toByte(),
      wheel.toByte()
    )
    val ok = hidDevice?.sendReport(device, REPORT_ID_MOUSE_ABS, report) ?: false
    if (!ok) {
      promise.reject("SEND_FAILED", "Failed to send absolute mouse report")
      return
    }
    promise.resolve(true)
  }

  private fun registerApp() {
    val sdp = BluetoothHidDeviceAppSdpSettings(
      "Bridge KVM",
      "Keyboard + Mouse",
      "BridgeKVM",
      BluetoothHidDevice.SUBCLASS1_COMBO,
      HID_REPORT_DESCRIPTOR
    )
    val qos = BluetoothHidDeviceAppQosSettings(
      BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
      0,
      0,
      0,
      0,
      0
    )
    executor.execute {
      val ok = hidDevice?.registerApp(sdp, null, qos, executor, hidCallback) ?: false
      if (!ok) {
        Log.e(TAG, "Failed to register HID app")
      }
    }
  }

  private fun emitConnectionState(state: Int) {
    mainHandler.post {
      val payload = Arguments.createMap().apply {
        putInt("state", state)
      }
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("hidConnectionState", payload)
    }
  }

  companion object {
    private const val TAG = "BluetoothHidModule"

    private const val REPORT_ID_KEYBOARD = 1
    private const val REPORT_ID_MOUSE_REL = 2
    private const val REPORT_ID_MOUSE_ABS = 3

    private const val ABS_MAX = 0x7FFF

    private val HID_REPORT_DESCRIPTOR = intArrayOf(
      // Keyboard
      0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
      0x85, REPORT_ID_KEYBOARD,
      0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,
      0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
      0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
      0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
      0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
      0xC0,
      // Mouse (relative)
      0x05, 0x01, 0x09, 0x02, 0xA1, 0x01,
      0x85, REPORT_ID_MOUSE_REL,
      0x09, 0x01, 0xA1, 0x00,
      0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
      0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
      0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
      0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
      0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
      0xC0,
      0xC0,
      // Mouse (absolute)
      0x05, 0x01, 0x09, 0x02, 0xA1, 0x01,
      0x85, REPORT_ID_MOUSE_ABS,
      0x09, 0x01, 0xA1, 0x00,
      0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
      0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
      0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
      0x05, 0x01, 0x09, 0x30, 0x09, 0x31,
      0x15, 0x00, 0x26, 0xFF, 0x7F,
      0x75, 0x10, 0x95, 0x02, 0x81, 0x02,
      0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
      0xC0,
      0xC0
    ).map { it.toByte() }.toByteArray()
  }
}
