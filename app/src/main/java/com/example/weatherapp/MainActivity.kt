package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient  // to get the lat and lng of user
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

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

        // setup sharedPreferences to load data before requesting API call
        // this way the UI will not be empty while getting the response
        mSharedPreferences = getSharedPreferences(Constants.PREFERANCE_NAME, Context.MODE_PRIVATE)
        setupUI()

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
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if (Constants.isNetworkAvailable(this@MainActivity)){

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)
            val listCall : Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT ,Constants.APP_ID)

            showProgressDialog()
            lifecycleScope.launch(Dispatchers.Main) {

                // make the API Get call
                listCall.enqueue(object: Callback<WeatherResponse>{

                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                        Log.e("Error", t.message.toString())
                        cancelProgressDialog()
                    }

                    override fun onResponse(
                        call: Call<WeatherResponse>,
                        response: Response<WeatherResponse>
                    ) {

                        cancelProgressDialog()
                        if (response.isSuccessful){
                            val weatherList: WeatherResponse = response.body()!!

                            // set SharedPreferences data here with the API response
                            // assign it to a Constant to use it later
                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            val editor = mSharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                            editor.apply()

                            Log.i("Response Result: ", "$weatherList")
                        }else{
                            when(response.code()){
                                400 -> {
                                    Log.e("Error 400","Bad Connection")
                                }
                                404 -> {
                                    Log.e("Error 404","Not Found")
                                }
                                else -> {
                                    Log.e("Error","Generic Error")
                                }
                            }
                        }
                    }
                })
            }
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

    // create the menu and add refresh button
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // when refresh button is clicked, request new data and refresh page
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.actionRefresh -> {
                requestLocationData()
                true
            }else ->{
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private fun showProgressDialog(){
        mProgressDialog = Dialog(this@MainActivity)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun cancelProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI(){
        // get the already assigned Constant response json data to a new val
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            // convert json data to WeatherResponse model
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (i in weatherList.weather.indices){

                binding?.tvCountry?.text = weatherList.sys.country
                binding?.tvName?.text = weatherList.name

                binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
                binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"

                binding?.tvSpeed?.text = weatherList.wind.speed.toString()
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + " per cent"
                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    binding?.tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales[0].toString())
                }else{
                    binding?.tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locale.country.toString())
                }

                when(weatherList.weather[i].icon){
                    // day
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sun)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.sunny_cloudy)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.just_clouds)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.dark_clouds)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rainy)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.stormy)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowy)
                    "50d" -> binding?.ivMain?.setImageResource(R.drawable.fog)
                    // night
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.moon)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.moon_cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.just_clouds)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.dark_clouds)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.rainy_night)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.stormy)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowy)
                    "50n" -> binding?.ivMain?.setImageResource(R.drawable.fog)
                }
            }
        }
    }

    private fun getUnit(localeValue: String): String{
        var value = "°C"
        if ("US" == localeValue || "LR" == localeValue || "MM" == localeValue){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val date = Instant
                .ofEpochMilli(timex * 1000L)
                .atZone(ZoneId.systemDefault()) // change time zone if necessary
                .toLocalDateTime()
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            return formatter.format(date)
        }else{
            val date = Date(timex * 1000L)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            return sdf.format(date)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binding != null){
            binding = null
        }
    }
}