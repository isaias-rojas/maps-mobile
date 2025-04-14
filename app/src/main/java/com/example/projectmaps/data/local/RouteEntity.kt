package com.example.projectmaps.data.local


import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.projectmaps.model.LocationPoint
import com.example.projectmaps.model.Route
import java.util.Date

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val locationsJson: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val startTimestamp: Long,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val endTimestamp: Long?
) {
    fun toRoute(): Route {
        // Convert JSON string to list of LocationPoint
        val locations = LocationConverters().fromJson(locationsJson)

        return Route(
            id = id,
            name = name,
            startTime = Date(startTime),
            endTime = endTime?.let { Date(it) },
            locations = locations,
            startPoint = LocationPoint(startLatitude, startLongitude, startTimestamp),
            endPoint = if (endLatitude != null && endLongitude != null && endTimestamp != null) {
                LocationPoint(endLatitude, endLongitude, endTimestamp)
            } else null
        )
    }

    companion object {
        fun fromRoute(route: Route): RouteEntity {
            return RouteEntity(
                id = route.id,
                name = route.name,
                startTime = route.startTime.time,
                endTime = route.endTime?.time,
                locationsJson = LocationConverters().toJson(route.locations),
                startLatitude = route.startPoint.latitude,
                startLongitude = route.startPoint.longitude,
                startTimestamp = route.startPoint.timestamp,
                endLatitude = route.endPoint?.latitude,
                endLongitude = route.endPoint?.longitude,
                endTimestamp = route.endPoint?.timestamp
            )
        }
    }
}

class LocationConverters {
    private val gson = com.google.gson.Gson()

    fun toJson(locations: List<LocationPoint>): String {
        return gson.toJson(locations)
    }

    fun fromJson(json: String): List<LocationPoint> {
        val type = object : com.google.gson.reflect.TypeToken<List<LocationPoint>>() {}.type
        return gson.fromJson(json, type)
    }
}