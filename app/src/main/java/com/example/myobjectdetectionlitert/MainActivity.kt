package com.example.myobjectdetectionlitert

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.myobjectdetectionlitert.model.TFLiteHelper
import com.example.myobjectdetectionlitert.ui.CameraScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    private lateinit var tfliteHelper: TFLiteHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TFLiteHelper
        try {
            tfliteHelper = TFLiteHelper(this)
            Log.d("MainActivity", "TFLiteHelper initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing TFLiteHelper", e)
        }

        setContent {
            MaterialTheme {
                Surface {
                    RequestCameraPermission {
                        // Show Camera Screen with TFLite processing
                        CameraScreen(tfliteHelper)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestCameraPermission(onPermissionGranted: @Composable () -> Unit) {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    if (permissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        Text(text = "Camera permission is required!")
    }
}