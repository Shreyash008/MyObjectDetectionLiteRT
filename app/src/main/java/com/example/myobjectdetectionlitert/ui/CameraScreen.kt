package com.example.myobjectdetectionlitert.ui

import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myobjectdetectionlitert.model.Detection
import com.example.myobjectdetectionlitert.model.TFLiteHelper
import java.util.concurrent.Executors

@Composable
fun CameraScreen(tfliteHelper: TFLiteHelper) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detections by remember { mutableStateOf(emptyList<Detection>()) }

    // Create an executor for image analysis
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Clean up executor when the composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    // State for preview dimensions and processing flag
    var previewWidth by remember { mutableStateOf(0) }
    var previewHeight by remember { mutableStateOf(0) }
    var processingImage by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(0f) }
    var lastFpsUpdateTime by remember { mutableStateOf(0L) }
    var frameCount by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Set up high resolution for better detection
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analyzer ->
                            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                // Update FPS calculation
                                val currentTime = System.currentTimeMillis()
                                frameCount++

                                if (currentTime - lastFpsUpdateTime >= 1000) {
                                    fps = frameCount * 1000f / (currentTime - lastFpsUpdateTime)
                                    frameCount = 0
                                    lastFpsUpdateTime = currentTime
                                }

                                if (!processingImage) {
                                    processingImage = true

                                    try {
                                        // Update preview dimensions for drawing
                                        if (previewWidth == 0) {
                                            previewWidth = previewView.width
                                            previewHeight = previewView.height
                                            Log.d("CameraScreen", "Preview dimensions: ${previewWidth}x${previewHeight}")
                                        }

                                        // Process image and update detections
                                        val results = tfliteHelper.processImage(imageProxy)
                                        detections = results

                                        Log.d("CameraScreen", "Found ${results.size} detections")
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Error analyzing image", e)
                                        imageProxy.close()
                                    } finally {
                                        processingImage = false
                                    }
                                } else {
                                    // Skip processing this frame
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        // Unbind all use cases before rebinding
                        cameraProvider.unbindAll()

                        // Bind use cases to camera
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )

                        Log.d("CameraScreen", "Camera setup complete")
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Draw detection overlays
        DetectionOverlay(
            detections = detections,
            previewWidth = previewWidth,
            previewHeight = previewHeight
        )

        // Display debug information
        Text(
            text = "Detections: ${detections.size} | FPS: ${String.format("%.1f", fps)}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}

@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    previewWidth: Int,
    previewHeight: Int
) {
    if (previewWidth <= 0 || previewHeight <= 0) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Scale factors to convert coordinates to screen coordinates
        val scaleX = canvasWidth / previewWidth
        val scaleY = canvasHeight / previewHeight

        // Custom colors for different classes (reuse for classes beyond this array)
        val classColors = arrayOf(
            Color(0xFFFF0000),  // Red
            Color(0xFF00FF00),  // Green
            Color(0xFF0000FF),  // Blue
            Color(0xFFFFFF00),  // Yellow
            Color(0xFF00FFFF),  // Cyan
            Color(0xFFFF00FF),  // Magenta
            Color(0xFFFF8000),  // Orange
            Color(0xFF8000FF)   // Purple
        )

        detections.forEach { detection ->
            // Get color based on class ID
            val color = classColors[detection.classId % classColors.size]

            val boxPaint = Paint().apply {
                this.color = color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }

            val textPaint = Paint().apply {
                this.color = Color.White.toArgb()
                textSize = 36f
                style = Paint.Style.FILL
                setShadowLayer(3f, 0f, 0f, Color.Black.toArgb())
            }

            val textBackgroundPaint = Paint().apply {
                this.color = Color(0x99000000).toArgb()  // Semi-transparent black
                style = Paint.Style.FILL
            }

            // YOLOv5 outputs center coordinates and dimensions
            // Convert to screen coordinates
            val centerX = detection.x * previewWidth * scaleX
            val centerY = detection.y * previewHeight * scaleY
            val boxWidth = detection.width * previewWidth * scaleX
            val boxHeight = detection.height * previewHeight * scaleY

            val left = centerX - (boxWidth / 2)
            val top = centerY - (boxHeight / 2)
            val right = centerX + (boxWidth / 2)
            val bottom = centerY + (boxHeight / 2)

            // Draw bounding box
            drawIntoCanvas { canvas ->
                val rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                canvas.nativeCanvas.drawRect(rect, boxPaint)

                // Format label text with confidence percentage
                val label = "${detection.className}: ${(detection.confidence * 100).toInt()}%"

                // Measure text to create background
                val textBounds = Rect()
                textPaint.getTextBounds(label, 0, label.length, textBounds)

                // Position label at top of box with padding
                val textLeft = left
                val textTop = top - 5

                // Draw text background
                canvas.nativeCanvas.drawRect(
                    textLeft,
                    textTop - textBounds.height(),
                    textLeft + textBounds.width() + 10,
                    textTop + 5,
                    textBackgroundPaint
                )

                // Draw label text
                canvas.nativeCanvas.drawText(label, textLeft, textTop, textPaint)
            }
        }
    }
}