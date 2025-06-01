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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
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
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Build

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

private fun addGeofence(context: Context, lat: Double, lng: Double, radius: Float, id: String) {
    val geofence = Geofence.Builder()
        .setRequestId(id)
        .setCircularRegion(lat, lng, radius)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
        .build()

    val geofencingRequest = GeofencingRequest.Builder()
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
        .addGeofence(geofence)
        .build()

    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val geofencingClient = LocationServices.getGeofencingClient(context)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
        return
    }

    geofencingClient.addGeofences(geofencingRequest, pendingIntent)
        .addOnSuccessListener {
            Toast.makeText(context, "Geofence added: $id", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { exception ->
            Toast.makeText(context, "Failed to add geofence: ${exception.message}", Toast.LENGTH_LONG).show()
        }
}

private fun removeGeofence(context: Context, id: String) {
    val geofencingClient = LocationServices.getGeofencingClient(context)
    geofencingClient.removeGeofences(listOf(id))
        .addOnSuccessListener {
            Toast.makeText(context, "Geofence removed: $id", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { exception ->
            Toast.makeText(context, "Failed to remove geofence: ${exception.message}", Toast.LENGTH_LONG).show()
        }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var locationText by remember { mutableStateOf("Fetching location...") }
    var dndStatus by remember { mutableStateOf("Checking...") }

    var showDialog by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    var selectedGeofence by remember { mutableStateOf<GeofenceData?>(null) }
    var geofences by remember { mutableStateOf(listOf<GeofenceData>()) }

    val mockLocationHelper = remember { MockLocationHelper(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
        } else true

        if (!fineLocationGranted) {
            Toast.makeText(context, "Fine location permission denied", Toast.LENGTH_SHORT).show()
        }
        if (!backgroundLocationGranted) {
            Toast.makeText(context, "Background location permission needed for geofencing", Toast.LENGTH_LONG).show()
        }
    }

    // Function to update DND status
    fun updateDNDStatus() {
        dndStatus = DNDHelper.getCurrentDNDStatus(context)
    }

    // Request permissions and update DND status on startup
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
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

        updateDNDStatus()
    }

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

            Spacer(modifier = Modifier.height(16.dp))

            // DND Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = "Do Not Disturb Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = dndStatus, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { updateDNDStatus() }
                        ) {
                            Text("Refresh")
                        }

                        if (!DNDHelper.hasPermission(context)) {
                            Button(
                                onClick = { DNDHelper.requestPermission(context) }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Grant Permission")
                            }
                        }
                    }

                    if (!DNDHelper.hasPermission(context)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ DND permission required for automatic control",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Testing Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Testing Controls",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mockLocationHelper.enableMockLocation() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Enable Mock")
                        }
                        Button(
                            onClick = { mockLocationHelper.disableMockLocation() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disable Mock")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    geofences.forEach { geofence ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = geofence.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    val lat = geofence.latitude.toDoubleOrNull() ?: 0.0
                                    val lng = geofence.longitude.toDoubleOrNull() ?: 0.0
                                    mockLocationHelper.setMockLocation(lat, lng)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Inside", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    val lat = geofence.latitude.toDoubleOrNull() ?: 0.0
                                    val lng = geofence.longitude.toDoubleOrNull() ?: 0.0
                                    mockLocationHelper.setMockLocation(lat + 0.001, lng + 0.001)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Outside", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                                removeGeofence(context, geofence.name)
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
                        val lat = latitude.toDoubleOrNull()
                        val lng = longitude.toDoubleOrNull()
                        val rad = radius.toFloatOrNull()

                        if (lat == null || lng == null || rad == null || name.isBlank()) {
                            Toast.makeText(context, "Please fill all fields with valid values", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        if (rad < 1f) {
                            Toast.makeText(context, "Radius should be at least 20 meters", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        val newGeofence = GeofenceData(
                            name = name,
                            latitude = latitude,
                            longitude = longitude,
                            radius = radius
                        )

                        selectedGeofence?.let { oldGeofence ->
                            removeGeofence(context, oldGeofence.name)
                        }

                        addGeofence(context, lat, lng, rad, name)

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
            title = { Text(if (selectedGeofence != null) "Edit Geofence" else "Add Geofence") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = radius,
                        onValueChange = { radius = it },
                        label = { Text("Radius (meters, min 20)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

data class GeofenceData(
    val name: String,
    val latitude: String,
    val longitude: String,
    val radius: String
)