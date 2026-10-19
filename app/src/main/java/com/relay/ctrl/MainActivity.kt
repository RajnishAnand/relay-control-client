package com.relay.ctrl
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NeuTheme {
            Box(Modifier.fillMaxSize().background(LocalPal.current.bg)) {
                val s by vm.s.collectAsStateWithLifecycle()
                Home(s, vm)
            }
        } }
    }
    override fun onStart() { super.onStart(); vm.setActive(true) }
    override fun onStop() { super.onStop(); vm.setActive(false) }
}
