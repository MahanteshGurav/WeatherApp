package com.mahantesh.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.mahantesh.weatherapp.Constants
import com.mahantesh.weatherapp.R
import com.mahantesh.weatherapp.models.WeatherResponse
import com.mahantesh.weatherapp.network.WeatherService
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Location is off, please turn it on", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "Location permission denied, please enable it as it is mandatory for the app to work.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }
                }).onSameThread()
                .check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            super.onLocationResult(locationResult)
            val mLastLocation: Location = locationResult!!.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude
            Log.e(TAG, "onLocationResult: $latitude $longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude,
                Constants.METRIC_UNIT,
                Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onFailure(t: Throwable?) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error:" + t!!.message.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                    hideProgressDialog()
                }

                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    hideProgressDialog()
                    if (response!!.isSuccess) {
                        val weatherList: WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.e(TAG, "onResponse: $weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "400, Bad Connection",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            404 -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "404, Not Found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Generic Error",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

            })

        } else {
            Toast.makeText(this, "No internet connection available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under application settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                Log.i(TAG, "setupUI: ${weatherList.weather.toString()}")
                tvMain.text = weatherList.weather[i].main
                tvMainDescription.text = weatherList.weather[i].description
                tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                tvMin.text = weatherList.main.temp_min.toString() + " min"
                tvMax.text = weatherList.main.temp_max.toString() + " max"
                tvSpeed.text = weatherList.wind.speed.toString()
                tvName.text = weatherList.name
                tvCountry.text = weatherList.sys.country

                tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                when(weatherList.weather[i].icon){
                    "01d" -> ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> ivMain.setImageResource(R.drawable.rain)
                    "11d" -> ivMain.setImageResource(R.drawable.storm)
                    "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> ivMain.setImageResource(R.drawable.rain)
                    "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    private fun getUnit(value: String) : String?{
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long):String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            } else -> super.onOptionsItemSelected(item)
        }
    }
}