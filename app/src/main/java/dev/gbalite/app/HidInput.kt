package dev.gbalite.app
import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.gbalite.input.GamepadMapping
import dev.gbalite.input.InputRouter

/** USB and system-paired Bluetooth HID share Android's input path. No pairing API. */
class HidInput(context: Context,private val router: InputRouter): InputManager.InputDeviceListener {
    private val manager=context.getSystemService(InputManager::class.java)
    fun start() { manager.registerInputDeviceListener(this,null) }
    fun stop() { manager.unregisterInputDeviceListener(this); router.releasePrefix("hid:") }
    fun key(event: KeyEvent): Boolean {
        val controller=event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.device?.supportsSource(InputDevice.SOURCE_GAMEPAD)==true || event.device?.supportsSource(InputDevice.SOURCE_JOYSTICK)==true
        if(event.keyCode==KeyEvent.KEYCODE_BACK && !controller) return false
        val button=GamepadMapping.keys[event.keyCode] ?: return false
        if(event.action !in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP)) return false
        router.update("hid:${event.deviceId}:key:${event.keyCode}",if(event.action==KeyEvent.ACTION_DOWN) setOf(button) else emptySet())
        return true
    }
    fun motion(event: MotionEvent): Boolean {
        if(!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.actionMasked!=MotionEvent.ACTION_MOVE) return false
        val device=event.device
        val flat=maxOf(device?.getMotionRange(MotionEvent.AXIS_X,event.source)?.flat ?: 0f,
            device?.getMotionRange(MotionEvent.AXIS_Y,event.source)?.flat ?: 0f)
        val keys=GamepadMapping.axes(event.getAxisValue(MotionEvent.AXIS_X),event.getAxisValue(MotionEvent.AXIS_Y),flat)+
            GamepadMapping.axes(event.getAxisValue(MotionEvent.AXIS_HAT_X),event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        router.update("hid:${event.deviceId}:axes",keys); return true
    }
    override fun onInputDeviceAdded(deviceId: Int) { router.releasePrefix("hid:$deviceId:") }
    override fun onInputDeviceRemoved(deviceId: Int) { router.releasePrefix("hid:$deviceId:") }
    override fun onInputDeviceChanged(deviceId: Int) { router.releasePrefix("hid:$deviceId:") }
}
