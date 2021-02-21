package com.tautech.cclapp.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tautech.cclapp.R
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    val REQUEST_LOCATION_PERMISSION = 1
    val TAG = "MAP_ACTIVITY"
    private var mMap: GoogleMap? = null
    var selectedLatLng: LatLng? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        doneLocationBtn.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("position", selectedLatLng))
            finish()
        }
        /*mGeoDataClient = GoogleApi.Places.getGeoDataClient(this, null);

        // Construct a PlaceDetectionClient.
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);*/
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        initLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }){
                initLocation()
            }
        }
    }

    fun initLocation(){
        if (mMap != null) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
                return
            }
            mMap?.isMyLocationEnabled = true
            /*mMap?.setOnMyLocationChangeListener { location ->
                mMap?.clear()
                selectedLatLng = LatLng(location.latitude,
                    location.longitude)
                Log.i(TAG, "boton de ubicacion presionado point: $selectedLatLng")
                if (selectedLatLng != null) {
                    mMap?.addMarker(MarkerOptions().position(selectedLatLng!!)
                        .title(getString(R.string.your_position)))
                    doneLocationBtn.visibility = View.VISIBLE
                    mMap?.moveCamera(CameraUpdateFactory.newLatLng(selectedLatLng))
                } else {
                    Toast.makeText(this, getString(R.string.invalid_selected_position), Toast.LENGTH_SHORT).show()
                }
            }*/
            mMap?.setOnMyLocationButtonClickListener {
                mMap?.clear()
                if (mMap?.myLocation?.latitude != null && mMap?.myLocation?.longitude != null) {
                    selectedLatLng = LatLng(mMap?.myLocation?.latitude!!,
                        mMap?.myLocation?.longitude!!)
                    Log.i(TAG, "boton de ubicacion presionado 2. position: $selectedLatLng")
                    if (selectedLatLng != null) {
                        mMap?.addMarker(MarkerOptions().position(selectedLatLng!!)
                            .title(getString(R.string.your_position)))
                        doneLocationBtn.visibility = View.VISIBLE
                        mMap?.moveCamera(CameraUpdateFactory.newLatLng(selectedLatLng))
                    } else {
                        Toast.makeText(this,
                            getString(R.string.invalid_selected_position),
                            Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            /*mMap?.setOnMyLocationClickListener { location ->
                mMap?.clear()
                selectedLatLng = LatLng(location.latitude,
                    location.longitude)
                Log.i(TAG, "boton de ubicacion presionado 3. location: $selectedLatLng")
                if (selectedLatLng != null) {
                    mMap?.addMarker(MarkerOptions().position(selectedLatLng!!)
                        .title(getString(R.string.your_position)))
                    doneLocationBtn.visibility = View.VISIBLE
                    mMap?.moveCamera(CameraUpdateFactory.newLatLng(selectedLatLng))
                } else {
                    Toast.makeText(this, getString(R.string.invalid_selected_position), Toast.LENGTH_SHORT).show()
                }
            }*/
        }
    }
}