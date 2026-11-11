package com.example.ev3pad

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = refresh()
        override fun onInputDeviceRemoved(id: Int) = refresh()
        override fun onInputDeviceChanged(id: Int) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.BLUETOOTH_CONNECT)

        getSystemService(InputManager::class.java).registerInputDeviceListener(listener, null)
        refresh()
        setContent { EV3Theme { MainScreen(vm) } }
    }

    override fun onDestroy() {
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(listener)
        super.onDestroy()
    }

    private fun refresh() {
        val dev = InputDevice.getDeviceIds().asSequence().mapNotNull { InputDevice.getDevice(it) }.firstOrNull { d ->
            !d.isVirtual &&
                ((d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
        }
        vm.setPad(dev != null, dev)
    }

    private fun keyPad(code: Int): Pad? = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> Pad.CRS
        KeyEvent.KEYCODE_BUTTON_B -> Pad.CIR
        KeyEvent.KEYCODE_BUTTON_X -> Pad.SQR
        KeyEvent.KEYCODE_BUTTON_Y -> Pad.TRI
        KeyEvent.KEYCODE_BUTTON_L1 -> Pad.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> Pad.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> Pad.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> Pad.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Pad.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Pad.R3
        KeyEvent.KEYCODE_BUTTON_SELECT -> Pad.SHARE
        KeyEvent.KEYCODE_BUTTON_START -> Pad.OPT
        KeyEvent.KEYCODE_BUTTON_MODE -> Pad.PS
        KeyEvent.KEYCODE_DPAD_UP -> Pad.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Pad.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Pad.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Pad.RIGHT
        // Touchpad PS4: tidak semua versi Android melaporkannya. Tambahkan kode tombolnya di sini jika perlu:
        // KeyEvent.KEYCODE_BUTTON_Z -> Pad.TP
        else -> null
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (e.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) {
            val p = keyPad(e.keyCode)
            if (p != null) {
                if (e.action == KeyEvent.ACTION_DOWN) vm.key(p, true)
                else if (e.action == KeyEvent.ACTION_UP) vm.key(p, false)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && e.action == MotionEvent.ACTION_MOVE) {
            vm.motion(e); return true
        }
        return super.dispatchGenericMotionEvent(e)
    }
}
