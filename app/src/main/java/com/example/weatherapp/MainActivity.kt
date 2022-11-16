package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient  // to get the lat and lng of user

    private var latitude = 0.0
    private var longitude = 0.0

    private val requestLocationPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
                permissions ->
            // Instead of forEach, use firstNotNullOfOrNull
            // otherwise Toast will pop up twice since Fine location also gives permission to coarse location
            permissions.entries.firstNotNullOfOrNull{
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted){
                    if (permissionName == Manifest.permission.ACCESS_COARSE_LOCATION ||
                        permissionName == Manifest.permission.ACCESS_FINE_LOCATION){
                        requestLocationData()
                    }

                }else{
                    showRationaleDialogForLocation()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        if (!isLocationEnabled()){
            Toast.makeText(this@MainActivity, "Location provider is turned off. Please turn it on", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        requestLocationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

        val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
//            .setMinUpdateIntervalMillis(100)
//            .setMaxUpdateDelayMillis(100)
            .setMaxUpdates(1)
            .build()

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            latitude = mLastLocation.latitude
            longitude = mLastLocation.longitude

            Log.i("Current latitude:", "$latitude")
            Log.i("Current longitude:", "$longitude")
            getLocationWeatherDetails()
        }
    }

    private fun getLocationWeatherDetails(){
        if (Constants.isNetworkAvailable(this@MainActivity)){
            Toast.makeText(this@MainActivity, "You have connected to internet.", Toast.LENGTH_LONG).show()
        }else{
            Toast.makeText(this@MainActivity, "No internet connection.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showRationaleDialogForLocation(){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Happy Places App")
            .setMessage("It looks like you have turned off permissions required for this feature." +
                    " It can be enabled under Application Settings"
            )
            .setNegativeButton("Cancel"){
                    dialog, _-> dialog.dismiss()
            }
            .setPositiveButton("GO TO SETTINGS"){
                // redirect user to app settings to allow permission for location
                    _, _->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }
            }
        builder.create().show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binding != null){
            binding = null
        }
    }

    override fun onResume() {
        super.onResume()
        finish()
        startActivity(intent)
    }
}