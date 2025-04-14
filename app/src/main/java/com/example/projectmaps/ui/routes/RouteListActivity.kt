package com.example.projectmaps.ui.routes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projectmaps.data.local.AppDatabase
import com.example.projectmaps.data.repository.RouteRepository
import com.example.projectmaps.model.Route
import com.example.projectmaps.ui.map.MapsActivity

import com.example.projectmaps.databinding.ActivityRouteListBinding

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RouteListActivity : AppCompatActivity(), RouteAdapter.RouteListener {

    private lateinit var binding: ActivityRouteListBinding
    private lateinit var routeAdapter: RouteAdapter
    private lateinit var routeRepository: RouteRepository

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (!locationGranted) {
            Toast.makeText(
                this,
                "Location permission is required to track routes",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val routeDao = AppDatabase.getDatabase(this).routeDao()
        routeRepository = RouteRepository(routeDao)

        setupRecyclerView()
        setupListeners()
        loadRoutes()
        checkLocationPermission()
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter(this)
        binding.recyclerRoutes.apply {
            layoutManager = LinearLayoutManager(this@RouteListActivity)
            adapter = routeAdapter
        }
    }

    private fun setupListeners() {
        binding.fabAddRoute.setOnClickListener {
            if (hasLocationPermission()) {
                startActivity(Intent(this, MapsActivity::class.java).apply {
                    putExtra(MapsActivity.EXTRA_MODE, MapsActivity.MODE_CREATE)
                })
            } else {
                showLocationPermissionNeededDialog()
            }
        }
    }

    private fun loadRoutes() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            routeRepository.getAllRoutes().collectLatest { routes ->
                binding.progressBar.visibility = View.GONE

                if (routes.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerRoutes.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerRoutes.visibility = View.VISIBLE
                    routeAdapter.submitList(routes)
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showLocationPermissionNeededDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to track your routes. Please grant location permission.")
            .setPositiveButton("Grant Permission") { _, _ ->
                checkLocationPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRouteClick(route: Route) {
        startActivity(Intent(this, MapsActivity::class.java).apply {
            putExtra(MapsActivity.EXTRA_MODE, MapsActivity.MODE_VIEW)
            putExtra(MapsActivity.EXTRA_ROUTE_ID, route.id)
        })
    }

    override fun onDeleteClick(route: Route) {
        AlertDialog.Builder(this)
            .setTitle("Delete Route")
            .setMessage("Are you sure you want to delete this route?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    routeRepository.deleteRoute(route.id)
                    Toast.makeText(
                        this@RouteListActivity,
                        "Route deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}