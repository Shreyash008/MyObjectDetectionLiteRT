package com.example.myobjectdetectionlitert.model

data class Detection(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val classId: Int,
    val className: String
)
