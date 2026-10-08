package dev.gbalite.mgba

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.test.runner.AndroidJUnitRunner

/** TEST APK ONLY; no permissions, service, production lifecycle or core shortcut. */
class JniHarnessActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {text="GBA Lite JNI tests running. Please keep this screen visible."})
    }
}
class ForegroundJniRunner: AndroidJUnitRunner() {
    private var harness: Activity?=null
    override fun onStart() {
        harness=startActivitySync(Intent(targetContext,JniHarnessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        super.onStart()
    }
    override fun finish(resultCode: Int,results: Bundle?) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {harness?.finish();harness=null}
        super.finish(resultCode,results)
    }
}
