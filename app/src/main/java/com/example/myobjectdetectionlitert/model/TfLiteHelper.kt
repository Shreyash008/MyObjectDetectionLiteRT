package com.example.myobjectdetectionlitert.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op

class TFLiteHelper(context: Context) {
    private var interpreter: Interpreter
    private val classLabels: List<String>
    private val inputWidth: Int
    private val inputHeight: Int
    private val numClasses: Int

    companion object {
        private const val MODEL_FILE = "YOLOv5.tflite"
        private const val LABELS_FILE = "classes.txt"
        private const val DETECTION_THRESHOLD = 0.25f
    }

    init {
        val modelFile = FileUtil.loadMappedFile(context, MODEL_FILE)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelFile, options)
        classLabels = loadClassLabels(context, LABELS_FILE)
        numClasses = classLabels.size

        // Get model dimensions from interpreter
        val inputShape = interpreter.getInputTensor(0).shape()
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]

        // Print model info for debugging
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d("TFLiteHelper", "Model Input Shape: ${inputShape.contentToString()}")
        Log.d("TFLiteHelper", "Model Output Shape: ${outputShape.contentToString()}")
        Log.d("TFLiteHelper", "Using input dimensions: ${inputWidth}x${inputHeight}")
        Log.d("TFLiteHelper", "Loaded ${classLabels.size} classes: ${classLabels}")
    }

    fun processImage(imageProxy: ImageProxy): List<Detection> {
        // Get image rotation
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Convert ImageProxy to bitmap
        val bitmap = imageProxy.toBitmap()

        // Create TensorImage from bitmap
        val tensorImage = TensorImage.fromBitmap(bitmap)

        // Process image - properly resize and normalize for YOLOv5
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(rotation / 90))  // Handle rotation based on camera orientation
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))  // YOLOv5 expects 0-1 normalization
            .build()

        val processedImage = imageProcessor.process(tensorImage)

        // Debug processed image
        Log.d("TFLiteHelper", "Image processed to ${inputWidth}x${inputHeight}, rotation: $rotation°")

        // Get output tensor shape and prepare output buffer
//        val outputTensor = interpreter.getOutputTensor(0)
        // Get output tensor shape
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d("TFLiteHelper", "Output shape: ${outputShape.contentToString()}")

        // Create proper output buffer based on shape
        val outputBuffer: Any = when (outputShape.size) {
            3 -> {
                // Shape [1, num_detections, values_per_detection]
                Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            }
            4 -> {
                // Shape [1, boxes, classes, values] - older YOLOv5 format
                Array(1) { Array(outputShape[1]) { Array(outputShape[2]) { FloatArray(outputShape[3]) } } }
            }
            else -> {
                Log.e("TFLiteHelper", "Unexpected output shape: ${outputShape.contentToString()}")
                imageProxy.close()
                return emptyList()
            }
        }

        // Run inference
        try {
            interpreter.run(processedImage.buffer, outputBuffer)
            Log.d("TFLiteHelper", "Inference completed successfully")
        } catch (e: Exception) {
            Log.e("TFLiteHelper", "Error running inference: ${e.message}", e)
            imageProxy.close()
            return emptyList()
        }

// Process output based on the format we created
        val detections = when (outputShape.size) {
            3 -> processDirectOutput(outputBuffer as Array<Array<FloatArray>>)
            4 -> processYoloOutput(outputBuffer as Array<Array<Array<FloatArray>>>)
            else -> emptyList()
        }

        imageProxy.close()
        return detections
    }

    private fun processDirectOutput(outputArray: Array<Array<FloatArray>>): List<Detection> {
        val detections = mutableListOf<Detection>()
        val imageWidth = inputWidth.toFloat()
        val imageHeight = inputHeight.toFloat()

        // Process each detection
        for (i in outputArray[0].indices) {
            val detection = outputArray[0][i]
            val confidence = detection[4]

            if (confidence < DETECTION_THRESHOLD) continue

            // Find class with highest score
            val classes = detection.sliceArray(5 until detection.size)
            val maxClassIndex = classes.indices.maxByOrNull { classes[it] } ?: continue
            val maxClassConfidence = classes[maxClassIndex]

            // Calculate final confidence
            val finalConfidence = confidence * maxClassConfidence
            if (finalConfidence < DETECTION_THRESHOLD) continue

            // Get normalized box coordinates [x_center, y_center, width, height]
            val x = detection[0]
            val y = detection[1]
            val width = detection[2]
            val height = detection[3]

            val className = if (maxClassIndex < classLabels.size) classLabels[maxClassIndex] else "Unknown"

            Log.d("TFLiteHelper", "Detection: class=$className($maxClassIndex), " +
                    "conf=${finalConfidence}, at [$x,$y,$width,$height]")

            detections.add(
                Detection(x, y, width, height, finalConfidence, maxClassIndex, className)
            )
        }

        return applyNMS(detections)
    }

    private fun processYoloOutput(outputArray: Array<Array<Array<FloatArray>>>): List<Detection> {
        // This is a placeholder for standard YOLOv5 grid-based output processing
        // Implementation would depend on your specific model's output format
        Log.d("TFLiteHelper", "Standard YOLOv5 output format detected - " +
                "this requires custom post-processing")
        return emptyList()
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return detections

        // Group detections by class
        val detectionsByClass = detections.groupBy { it.classId }
        val result = mutableListOf<Detection>()

        // Apply NMS per class
        detectionsByClass.forEach { (_, classDetections) ->
            val sorted = classDetections.sortedByDescending { it.confidence }
            val selected = mutableListOf<Detection>()

            for (detection in sorted) {
                var shouldSelect = true

                // Check if this detection overlaps too much with any selected detection
                for (selectedDetection in selected) {
                    val iou = calculateIoU(detection, selectedDetection)
                    if (iou > 0.45f) {  // IoU threshold
                        shouldSelect = false
                        break
                    }
                }

                if (shouldSelect) {
                    selected.add(detection)
                }
            }

            result.addAll(selected)
        }

        return result
    }

    private fun calculateIoU(a: Detection, b: Detection): Float {
        // Calculate coordinates of intersection rectangle
        val xMin = maxOf(a.x - a.width/2, b.x - b.width/2)
        val yMin = maxOf(a.y - a.height/2, b.y - b.height/2)
        val xMax = minOf(a.x + a.width/2, b.x + b.width/2)
        val yMax = minOf(a.y + a.height/2, b.y + b.height/2)

        // No intersection
        if (xMin >= xMax || yMin >= yMax) return 0f

        val intersection = (xMax - xMin) * (yMax - yMin)
        val aArea = a.width * a.height
        val bArea = b.width * b.height
        val union = aArea + bArea - intersection

        return if (union > 0) intersection / union else 0f
    }

    private fun loadClassLabels(context: Context, fileName: String): List<String> {
        return try {
            context.assets.open(fileName).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.toList()
            }
        } catch (e: Exception) {
            Log.e("TFLiteHelper", "Error loading class labels: ${e.message}")
            emptyList()
        }
    }
}

// Extension function to convert ImageProxy to Bitmap
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width

    // Create bitmap
    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)
    return bitmap
}