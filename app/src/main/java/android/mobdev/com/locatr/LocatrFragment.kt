package android.mobdev.com.locatr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.*
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import java.io.IOException
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions


class LocatrFragment : SupportMapFragment() {
    private lateinit var mClient: GoogleApiClient
    private var mMap: GoogleMap? = null
    private var mMapImage: Bitmap? = null
    private var mMapItem: GalleryItem? = null
    private var mCurrentLocation: Location? = null

    companion object {
        private val LOCATION_PERMISSIONS =
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private val TAG = "LocatrFragment"
        private val REQUEST_LOCATION_PERMISSIONS = 0

        fun newInstance(): LocatrFragment {
            return LocatrFragment()
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.invalidateOptionsMenu()
        mClient?.connect()
    }

    override fun onStop() {
        super.onStop()
        mClient?.disconnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mClient = GoogleApiClient.Builder(activity as Context)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {
                    activity?.invalidateOptionsMenu()
                }

                override fun onConnectionSuspended(i: Int) {}
            })
            .build()
        getMapAsync { googleMap ->
            mMap = googleMap
            updateUI()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.fragment_locatr, menu)

        val searchItem = menu?.findItem(R.id.action_locate)
        var t = mClient.isConnected
        searchItem?.isEnabled = mClient.isConnected
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_locate -> {
                if (hasLocationPermission()) {
                    findImage()
                } else {
                    requestPermissions(
                        LOCATION_PERMISSIONS,
                        REQUEST_LOCATION_PERMISSIONS
                    )
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSIONS ->
                if (hasLocationPermission()) {
                    findImage()
                } else super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @SuppressLint("MissingPermission")
    fun findImage() {
        val request = LocationRequest.create()
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        request.numUpdates = 1
        request.interval = 0
        val mFusedLocationClient = activity?.let { it }?.let { LocationServices.getFusedLocationProviderClient(it) }
        activity?.let {
            mFusedLocationClient?.lastLocation?.addOnSuccessListener(it) { location ->
                Log.i(TAG, "Got a fix: $location")
                SearchTask().execute(location)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        var result = activity?.let { ContextCompat.checkSelfPermission(it, LOCATION_PERMISSIONS[0]) }
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun updateUI() {
        if (mMap == null || mMapImage == null) {
            return
        }
        val itemPoint =
            mMapItem?.mLat?.let { mMapItem?.mLon?.let { it1 -> LatLng(it, it1) } }
        val myPoint =
            mCurrentLocation?.latitude?.let { mCurrentLocation?.longitude?.let { it1 -> LatLng(it, it1) } }

        val itemBitmap = BitmapDescriptorFactory.fromBitmap(mMapImage)
        val itemMarker = itemPoint?.let {
            MarkerOptions()
                .position(it)
                .icon(itemBitmap)
        }
        val myMarker = myPoint?.let { it1 -> MarkerOptions().position(it1) }
        mMap?.clear()
        mMap?.addMarker(itemMarker)
        mMap?.addMarker(myMarker)

        val bounds = LatLngBounds.Builder()
            .include(itemPoint)
            .include(myPoint)
            .build()
        val margin = resources.getDimensionPixelSize(R.dimen.map_inset_margin)
        val update = CameraUpdateFactory.newLatLngBounds(bounds, margin)
        mMap?.animateCamera(update)
    }

    private inner class SearchTask : AsyncTask<Location?, Void, Void>() {
        private var mGalleryItem: GalleryItem? = null
        private var mBitmap: Bitmap? = null
        private var mLocation: Location? = null

        override fun doInBackground(vararg params: Location?): Void? {
            mLocation = params[0]
            val fetchr = FlickrFetchr()
            val items = params[0]?.let { fetchr.searchPhotos(it) }
            if (items?.size == 0) {
                return null
            }
            mGalleryItem = items?.get(0)
            try {
                val bytes = mGalleryItem?.mUrl?.let { fetchr.getUrlBytes(it) }
                mBitmap = bytes?.size?.let { BitmapFactory.decodeByteArray(bytes, 0, it) }
            } catch (ioe: IOException) {
                Log.i(TAG, "Unable to download bitmap", ioe)
            }

            return null
        }


        override fun onPostExecute(result: Void?) {
            mMapImage = mBitmap
            mMapItem = mGalleryItem
            mCurrentLocation = mLocation

            updateUI()
        }
    }
}