package com.example.projectmaps.ui.map



import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.projectmaps.R
import com.example.projectmaps.data.local.AppDatabase
import com.example.projectmaps.data.repository.RouteRepository
import com.example.projectmaps.databinding.ActivityMapsBinding
import com.example.projectmaps.model.LocationPoint
import com.example.projectmaps.model.Route
import com.example.projectmaps.util.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val MODE_CREATE = "mode_create"
        const val MODE_VIEW = "mode_view"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var map: GoogleMap
    private lateinit var locationHelper: LocationHelper
    private lateinit var routeRepository: RouteRepository

    private var currentMode = MODE_CREATE
    private var routeId: Long = -1
    private var isTracking = false
    private val locationPoints = mutableListOf<LocationPoint>()
    private var startTime: Date? = null
    private var routeName: String = ""

    private var pendingRouteToLoad = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        routeRepository = RouteRepository(database.routeDao())

        locationHelper = LocationHelper(this)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CREATE
        routeId = intent.getLongExtra(EXTRA_ROUTE_ID, -1)

        if (currentMode == MODE_VIEW && routeId != -1L) {
            pendingRouteToLoad = true
        }



        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
    }

    private fun setupUI() {
        when (currentMode) {
            MODE_CREATE -> {
                binding.btnStartStop.text = "Start Tracking"
                binding.btnSave.isEnabled = false
                binding.tvRouteStatus.text = "Ready to Track"
                binding.tvRouteInfo.text = "Press Start to begin tracking your route"

                binding.btnStartStop.setOnClickListener {
                    if (!isTracking) {
                        startTracking()
                    } else {
                        stopTracking()
                    }
                }

                binding.btnSave.setOnClickListener {
                    showSaveRouteDialog()
                }
            }
            MODE_VIEW -> {
                binding.btnStartStop.text = "Back"
                binding.btnSave.isEnabled = false
                binding.btnSave.visibility = android.view.View.GONE
                binding.tvRouteStatus.text = "Viewing Route"

                binding.btnStartStop.setOnClickListener {
                    finish()
                }

                loadRoute(routeId)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }

        if (pendingRouteToLoad) {
            loadRoute(routeId)
        }
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        startTime = Date()
        isTracking = true
        locationPoints.clear()

        binding.btnStartStop.text = "Stop Tracking"
        binding.tvRouteStatus.text = "Tracking Route"
        binding.tvRouteInfo.text = "Recording your route..."

        locationHelper.startLocationUpdates { location ->
            onLocationUpdate(location)
        }
    }

    private fun stopTracking() {
        isTracking = false
        locationHelper.stopLocationUpdates()

        binding.btnStartStop.text = "Start New Tracking"
        binding.btnSave.isEnabled = true
        binding.tvRouteStatus.text = "Route Completed"
        binding.tvRouteInfo.text = "Press Save to store your route"
    }

    private fun onLocationUpdate(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        val locationPoint = LocationPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )
        locationPoints.add(locationPoint)

        updateRouteOnMap()

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
    }

    private fun updateRouteOnMap() {
        if (locationPoints.isEmpty()) return

        map.clear()

        val polylineOptions = PolylineOptions()
        for (point in locationPoints) {
            polylineOptions.add(LatLng(point.latitude, point.longitude))
        }
        polylineOptions.color(ContextCompat.getColor(this, R.color.purple_500))
        polylineOptions.width(12f)
        map.addPolyline(polylineOptions)

        val startPoint = locationPoints.first()
        map.addMarker(
            MarkerOptions()
                .position(LatLng(startPoint.latitude, startPoint.longitude))
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        val currentPoint = locationPoints.last()
        map.addMarker(
            MarkerOptions()
                .position(LatLng(currentPoint.latitude, currentPoint.longitude))
                .title("Current Position")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun showSaveRouteDialog() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val defaultName = "Route ${dateFormat.format(startTime ?: Date())}"

        val input = android.widget.EditText(this)
        input.setText(defaultName)

        AlertDialog.Builder(this)
            .setTitle("Save Route")
            .setMessage("Enter a name for this route:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                routeName = input.text.toString().takeIf { it.isNotBlank() } ?: defaultName
                saveRoute()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRoute() {
        if (locationPoints.isEmpty() || startTime == null) {
            Toast.makeText(this, "No route data to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val route = Route(
                id = 0,
                name = routeName,
                startTime = startTime!!,
                endTime = Date(),
                locations = locationPoints,
                startPoint = locationPoints.first(),
                endPoint = locationPoints.last()
            )

            val id = routeRepository.saveRoute(route)
            Toast.makeText(
                this@MapsActivity,
                "Route saved successfully!",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }

    private fun loadRoute(routeId: Long) {
        Log.d(TAG, "Loading route with ID: $routeId")
        lifecycleScope.launch {
            try {
                val route = routeRepository.getRouteById(routeId)
                Log.d(TAG, "Route loaded: ${route != null}")

                if (route != null) {
                    Log.d(TAG, "Route has ${route.locations.size} location points")
                    locationPoints.clear()
                    locationPoints.addAll(route.locations)

                    binding.tvRouteStatus.text = route.name
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.tvRouteInfo.text = "Recorded on: ${dateFormat.format(route.startTime)}"

                    if (::map.isInitialized) {
                        displayRouteOnMap(route)
                    } else {
                        Log.w(TAG, "Map not initialized yet, can't display route")
                        pendingRouteToLoad = true
                    }
                } else {
                    Log.w(TAG, "Route not found with ID: $routeId")
                    Toast.makeText(this@MapsActivity, "Route not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading route: ${e.message}", e)
                Toast.makeText(this@MapsActivity, "Error loading route", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayRouteOnMap(route: Route) {
        if (!::map.isInitialized) {
            Log.e(TAG, "Cannot display route: map not initialized")
            return
        }

        if (route.locations.isEmpty()) {
            Log.w(TAG, "Route has no locations to display")
            Toast.makeText(this, "This route has no location points", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Displaying route with ${route.locations.size} points")

        map.clear()

        // Draw route line
        val polylineOptions = PolylineOptions()
        for (point in route.locations) {
            polylineOptions.add(LatLng(point.latitude, point.longitude))
        }
        polylineOptions.color(ContextCompat.getColor(this, R.color.purple_500))
        polylineOptions.width(12f)
        map.addPolyline(polylineOptions)

        // Add start marker
        val startPoint = route.startPoint
        map.addMarker(
            MarkerOptions()
                .position(LatLng(startPoint.latitude, startPoint.longitude))
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        route.endPoint?.let { endPoint ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(endPoint.latitude, endPoint.longitude))
                    .title("End")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        try {
            if (route.locations.size > 1) {
                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                for (point in route.locations) {
                    boundsBuilder.include(LatLng(point.latitude, point.longitude))
                }
                val bounds = boundsBuilder.build()
                val padding = resources.displayMetrics.density.toInt() * 100 // 100dp padding

                binding.map.post {
                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                        Log.d(TAG, "Camera moved to show the entire route")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error moving camera: ${e.message}", e)
                        val firstPoint = route.locations.first()
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            LatLng(firstPoint.latitude, firstPoint.longitude), 15f))
                    }
                }
            } else {
                val point = route.locations.first()
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.latitude, point.longitude), 15f))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting camera position: ${e.message}", e)
        }

        Log.d(TAG, "Route displayed successfully")
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true

            locationHelper.getLastLocation { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for tracking routes",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
    }
}