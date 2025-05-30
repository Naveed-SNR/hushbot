package com.example.hushbot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Delete

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.hushbot.ui.theme.HushbotTheme
import com.google.android.gms.location.LocationServices
import androidx.compose.foundation.clickable



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HushbotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var locationText by remember { mutableStateOf("Fetching location...") }

    var showDialog by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    var selectedGeofence by remember { mutableStateOf<GeofenceData?>(null) }
    var geofences by remember { mutableStateOf(listOf<GeofenceData>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!granted) {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Fetch current location once
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        currentLocation = it
                        locationText = "Lat: ${it.latitude}, Lng: ${it.longitude}"
                    } ?: run {
                        locationText = "Location unavailable"
                    }
                }
        }
    }

    // Set values when dialog opens
    LaunchedEffect(showDialog, selectedGeofence) {
        if (showDialog) {
            latitude = selectedGeofence?.latitude ?: currentLocation?.latitude?.toString() ?: ""
            longitude = selectedGeofence?.longitude ?: currentLocation?.longitude?.toString() ?: ""
            radius = selectedGeofence?.radius ?: "50"
            name = selectedGeofence?.name ?: ""
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedGeofence = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Geofence")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Current location card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = "Current Location", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = locationText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        } else {
                            val fusedLocationClient =
                                LocationServices.getFusedLocationProviderClient(context)
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { location ->
                                    location?.let {
                                        currentLocation = it
                                        locationText =
                                            "Lat: ${it.latitude}, Lng: ${it.longitude}"
                                    } ?: run {
                                        locationText = "Location unavailable"
                                    }
                                }
                        }
                    }) {
                        Text("Refresh Location")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Geofence list
            if (geofences.isNotEmpty()) {
                Text(
                    text = "Geofences",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                geofences.forEach { geofence ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedGeofence = geofence
                                showDialog = true
                            },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = geofence.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Lat: ${geofence.latitude}, Lng: ${geofence.longitude}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Radius: ${geofence.radius}m",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = {
                                geofences = geofences.filter { it != geofence }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Geofence"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Geofence Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newGeofence = GeofenceData(
                            name = name,
                            latitude = latitude,
                            longitude = longitude,
                            radius = radius
                        )

                        geofences = if (selectedGeofence != null) {
                            geofences.map {
                                if (it == selectedGeofence) newGeofence else it
                            }
                        } else {
                            geofences + newGeofence
                        }

                        showDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Geofence Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = radius,
                        onValueChange = { radius = it },
                        label = { Text("Radius (meters)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}


// Geofence data class
data class GeofenceData(
    val name: String,
    val latitude: String,
    val longitude: String,
    val radius: String
)


