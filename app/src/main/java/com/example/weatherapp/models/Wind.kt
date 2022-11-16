package com.example.weatherapp.models

data class Wind(
    val speed: Double,
    val deg: Int,
    val gust: Double
): java.io.Serializable
