package com.example.projectmaps.model


import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Route(
    val id: Long = 0,
    val name: String,
    val startTime: Date,
    val endTime: Date?,
    val locations: List<LocationPoint>,
    val startPoint: LocationPoint,
    val endPoint: LocationPoint?
) : Parcelable

@Parcelize
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
) : Parcelable {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}