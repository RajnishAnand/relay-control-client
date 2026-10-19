package com.relay.ctrl
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Invisible entry point for assistants, shortcuts, Tasker/MacroDroid, NFC tags.
 *   relay://cmd?name=Porch%20Light&state=on|off|toggle[&device=192.168.1.250]
 *   relay://cmd?name=all&state=off
 * or action com.relay.ctrl.COMMAND with string extras name / state / device.
 */
class CommandActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val u = intent.data
        fun arg(k: String) = u?.getQueryParameter(k) ?: intent.getStringExtra(k)
        val state = when (arg("state")?.trim()?.lowercase()) {
            "on", "1", "true" -> "on"
            "off", "0", "false" -> "off"
            "toggle", "flip", null -> "toggle"
            else -> { say("State must be on, off or toggle"); return }
        }
        lifecycleScope.launch {
            val r = withTimeoutOrNull(60_000) { Commander.run(applicationContext, arg("name"), state, arg("device")) }
                ?: Commander.Result(false, "Timed out")
            say(r.msg)
        }
    }
    private fun say(m: String) { Toast.makeText(applicationContext, m, Toast.LENGTH_LONG).show(); finish() }
}
