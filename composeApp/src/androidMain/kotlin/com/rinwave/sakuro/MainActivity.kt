package com.rinwave.sakuro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.arkivanov.decompose.defaultComponentContext
import com.rinwave.sakuro.core.media.LibraryEvents
import com.rinwave.sakuro.di.AppDependencies
import com.rinwave.sakuro.navigation.RootComponent
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    private val requestMediaPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) LibraryEvents.requestRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deps = GlobalContext.get().get<AppDependencies>()
        val root = RootComponent(defaultComponentContext(), deps)

        setContent {
            App(root)
        }

        requestMediaPermissionIfNeeded()
    }

    private fun requestMediaPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestMediaPermission.launch(permission)
        }
    }
}
