package com.example.myobjectdetectionlitert.ui
//
//import android.graphics.Paint
//import androidx.compose.foundation.Canvas
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
//import androidx.compose.ui.graphics.nativeCanvas
//import com.example.myobjectdetectionlitert.model.Detection
//
//@Composable
//fun DrawDetections(detections: List<Detection>, classLabels: List<String>) {
//    Canvas(modifier = Modifier.fillMaxSize()) {
//        val textPaint = Paint().apply {
//            color = android.graphics.Color.RED
//            textSize = 40f
//        }
//
//        detections.forEach { detection ->
//            val label = classLabels.getOrNull(detection.classId) ?: "Unknown"
//
//            // Convert bounding box values to canvas dimensions
//            val left = detection.x
//            val top = detection.y
//            val right = detection.x + detection.width
//            val bottom = detection.y + detection.height
//
//            // Draw rectangle
//            drawRect(
//                color = Color.Red,
//                topLeft = androidx.compose.ui.geometry.Offset(left, top),
//                size = androidx.compose.ui.geometry.Size(detection.width, detection.height)
//            )
//
//            // Draw text (object name)
//            drawIntoCanvas { canvas ->
//                canvas.nativeCanvas.drawText(label, left, top - 10, textPaint)
//            }
//        }
//    }
//}
