package com.example.fooddelivery

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.location.Location
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.fooddelivery.ui.theme.FoodDeliveryTheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
//Clase MainActivity que hereda de ComponetActivity
class MainActivity : ComponentActivity() {

    // Declarar el cliente de ubicación, utilizado para obtener la ubicacion del dispositivo
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar FusedLocationProviderClient, permite acceder a la ubicacion del dispositivo
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //Activar diseño edge-to-edge
        enableEdgeToEdge()
        //Configurar la UI usando Jetpack Compose
        setContent {
            FoodDeliveryTheme {
                // Definir una estructura de pantalla con Scaffold, que gestiona la interfaz de usuario de la aplicación
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Llamar a la función que muestra el mapa y el resto de la interfaz
                    MapScreen(
                        modifier = Modifier.padding(innerPadding),
                        fusedLocationClient = fusedLocationClient,
                        context = this
                    )
                }
            }
        }

        // Verificar y solicitar permisos para notificaciones en Android 13 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    // Solicitar el permiso POST_NOTIFICATIONS en Android 13 y superiores
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,//Recibir el cliente de ubicacion
    context: Context //Recibi el contexto para manejar permisos
) {
    //Adminsitrar el estado del permiso de ubicacion
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    // Variable mutable para almacenar la ubicación actual del dispositivo
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    // Ubicación predeterminada en Santiago, Chile
    val defaultLocation = LatLng(-33.4489, -70.6693)
    // Estado de la cámara para el mapa de Google, centrándose en la ubicación predeterminada
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }
    // Variables mutables para almacenar el monto de compra, la distancia y el costo de despacho
    var purchaseAmount by remember { mutableDoubleStateOf(0.0) }
    var distance by remember { mutableDoubleStateOf(0.0) }
    var deliveryCost by remember { mutableDoubleStateOf(0.0) }
    // Variable para simular la temperatura del congelador
    var freezerTemperature by remember { mutableDoubleStateOf(0.0) }
    // Función que calcula el costo de despacho según el monto de la compra y la distancia
    fun calculateDeliveryCost(purchaseAmount: Double, distance: Double): Double {
        return when {
            purchaseAmount > 50000 -> 0.0
            purchaseAmount in 25000.0..49999.0 -> distance * 150
            else -> distance * 300
        }
    }
    // Función para mostrar una notificación persistente
    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        // Crear el canal de notificaciones si la versión es Android Oreo o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "freezer_alerts",
                "Alerta de congelador",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alerta cuando la temperatura del congelador excede el límite"
            }
            notificationManager.createNotificationChannel(channel)
        }
       // Crear la notificación con el título y mensaje especificados
        val notification: Notification = NotificationCompat.Builder(context, "freezer_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Mostrar la notificación si el permiso esta otorgado
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(1, notification)
        } else {
            Toast.makeText(context, "Permiso de notificaciones no otorgado", Toast.LENGTH_SHORT).show()
        }
    }
    // Función que verifica la temperatura del congelador y muestra una alerta si excede 4°C
    fun monitorFreezerTemperature() {
        if (freezerTemperature > 4.0) {
            showNotification(context, "¡Alerta de congelador!", "La temperatura ha excedido el límite permitido.")
        }
    }
    // Función para obtener la ubicación actual del dispositivo
    fun updateCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = LatLng(it.latitude, it.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLocation!!, 15f)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
        // Comprobar si el permiso de ubicación está otorgado
    if (locationPermissionState.hasPermission) {
        LaunchedEffect(Unit) {
            updateCurrentLocation()
        }
// Interfaz de usuario que muestra el mapa y permite calcular el costo de despacho y monitorear la temperatura
        Column(modifier = modifier.padding(16.dp)) {
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                cameraPositionState = cameraPositionState
            )
// Campo de entrada para el monto de la compra
            OutlinedTextField(
                value = purchaseAmount.toString(),
                onValueChange = { value -> purchaseAmount = value.toDoubleOrNull() ?: 0.0 },
                label = { Text("Monto de la compra") },
                modifier = Modifier.fillMaxWidth()
            )
// Campo de entrada para la distancia recorrida
            OutlinedTextField(
                value = distance.toString(),
                onValueChange = { value -> distance = value.toDoubleOrNull() ?: 0.0 },
                label = { Text("Distancia en km") },
                modifier = Modifier.fillMaxWidth()
            )
// Botón para calcular el costo de despacho
            Button(onClick = {
                deliveryCost = calculateDeliveryCost(purchaseAmount, distance)
            }) {
                Text("Calcular costo de despacho")
            }
// Mostrar el costo de despacho calculado
            Text(text = "Costo de despacho: $deliveryCost", modifier = Modifier.padding(top = 16.dp))
// Campo de entrada para la temperatura del congelador
            OutlinedTextField(
                value = freezerTemperature.toString(),
                onValueChange = { value -> freezerTemperature = value.toDoubleOrNull() ?: 0.0 },
                label = { Text("Temperatura del congelador (°C)") },
                modifier = Modifier.fillMaxWidth()
            )
// Botón para monitorear la temperatura del congelador
            Button(onClick = {
                monitorFreezerTemperature()
            }) {
                Text("Monitorear temperatura")
            }
        }

    } else {
        // Solicitar permiso de ubicación si no está otorgado
        LaunchedEffect(Unit) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapScreenPreview() {  // Vista previa simulada del mapa
    FoodDeliveryTheme {
        Text(text = "Vista previa del mapa (simulación)")
    }
}
