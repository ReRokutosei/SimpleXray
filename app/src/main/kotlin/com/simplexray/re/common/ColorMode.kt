package com.simplexray.re.common

enum class ColorMode(val value: String) {
    Monochrome("Monochrome"),
    Dynamic("Dynamic");

    companion object {
        fun fromString(value: String): ColorMode = when (value) {
            "Dynamic" -> Dynamic
            else -> Monochrome
        }
    }
}
