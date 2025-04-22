package com.example.myobjectdetectionlitert.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tflite.gpu.GpuDelegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Interpreter.Options
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ObjectDetectionHelper(context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    init {
        try {
            val tfliteModel = loadModelFile(context)
            val options = Options()

            // 🟢 Enable GPU Delegate
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)

            options.setNumThreads(4)  // Adjust based on your device

            interpreter = Interpreter(tfliteModel, options)
            Log.d("ObjectDetectionHelper", "TFLite Model Loaded with GPU Delegate")
        } catch (e: Exception) {
            Log.e("ObjectDetectionHelper", "Error loading model: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("YOLOv5.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun getTFLiteOptions(): Options {
        val options = Options()
        try {
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
            options.setNumThreads(4) // Adjust based on your device
        } catch (e: Exception) {
            Log.w("TFLite", "GPU Delegate not supported, using CPU.")
        }
        return options
    }

    fun processImage(bitmap: Bitmap): FloatArray {
        val inputBuffer: ByteBuffer = preprocessImage(bitmap)
        val outputBuffer = Array(1) { FloatArray(25200) }  // Adjust output shape if needed

        interpreter?.run(inputBuffer, outputBuffer)
        return outputBuffer[0]
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Convert Bitmap to ByteBuffer (Assuming model expects float input)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * 640 * 640 * 4)
        inputBuffer.rewind()

        val pixels = IntArray(640 * 640)
        bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        return inputBuffer
    }

    private fun imageToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val vuBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputShape = intArrayOf(1, 640, 640, 3) // Ensure matches model input
        val byteBuffer = ByteBuffer.allocateDirect(640 * 640 * 3 * 4) // 4 bytes per float32
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(640 * 640)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        scaledBitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)

        for (pixel in intValues) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            byteBuffer.putFloat(r / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(b / 255.0f)
        }
        return byteBuffer
    }

    private fun postProcessDetections(output: Array<FloatArray>): List<String> {
        val detectedObjects = mutableListOf<String>()
        for (detection in output) {
            val confidence = detection[4] // Confidence score
            if (confidence > 0.4) { // Set threshold as needed
                detectedObjects.add("Class: ${detection[5]}, Confidence: $confidence")
            }
        }
        return detectedObjects
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
