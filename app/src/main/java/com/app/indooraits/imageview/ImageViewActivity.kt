package com.app.indooraits.imageview

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.app.indooraits.R
import com.app.indooraits.utils.ExampleUtils.showInfo
import com.davemorrissey.labs.subscaleview.ImageSource
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.indooratlas.android.sdk.IALocation
import com.indooratlas.android.sdk.IALocationListener
import com.indooratlas.android.sdk.IALocationManager
import com.indooratlas.android.sdk.IALocationRequest
import com.indooratlas.android.sdk.IAOrientationListener
import com.indooratlas.android.sdk.IAOrientationRequest
import com.indooratlas.android.sdk.IAPOI
import com.indooratlas.android.sdk.IARegion
import com.indooratlas.android.sdk.IARoute
import com.indooratlas.android.sdk.IAWayfindingListener
import com.indooratlas.android.sdk.IAWayfindingRequest
import com.indooratlas.android.sdk.IAWayfindingTags
import com.indooratlas.android.sdk.resources.IAFloorPlan
import com.indooratlas.android.sdk.resources.IALatLng
import com.indooratlas.android.sdk.resources.IALocationListenerSupport
import com.indooratlas.android.sdk.resources.IAVenue
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom

//@SdkExample(description = R.string.example_imageview_description)
class ImageViewActivity : FragmentActivity() {
    private var mWayfindingDestination: IAWayfindingRequest? = null
    private var mIALocationManager: IALocationManager? = null
    private var mFloorPlan: IAFloorPlan? = null
    private var mImageView: BlueDotView? = null
    private var mLoadTarget: com.squareup.picasso.Target? = null
    private var mVenue: IAVenue? = null
    private var mFloor = 0
    private var mAccessibleRoute = true
    private  val REQUEST_CODE_PERMISSIONS = 1001
    private  val TAG = "IndoorAtlasExample"

    private var lastPoint: PointF? = null
    private var mCurrentRoute: IARoute? = null
    private val mPolylines: MutableList<Polyline> = ArrayList<Polyline>()


    private lateinit var etSearch: EditText
    private lateinit var listSearch: ListView

    private val allPois: MutableList<IAPOI> = ArrayList()
    private val filteredPois: MutableList<IAPOI> = ArrayList()
    private val poiMarkerMap = HashMap<String, BlueDotView.POIMarker>()
    private val mPoIMarkers: MutableList<BlueDotView.POIMarker> = ArrayList<BlueDotView.POIMarker>()
    private lateinit var adapter: ArrayAdapter<String>


    private val mLocationListener: IALocationListener = object : IALocationListenerSupport() {
        override fun onLocationChanged(location: IALocation) {
            Log.d(TAG, "location is: " + location.getLatitude() + "," + location.getLongitude())
            if (mImageView != null && mImageView!!.isReady()) {

                val newFloor = location.getFloorLevel()
                if (mFloor != newFloor) {
                    updateRouteVisualization()
                }
                mFloor = newFloor
                val latLng = IALatLng(location.getLatitude(), location.getLongitude())
                val point = mFloorPlan!!.coordinateToPoint(latLng)
               /* mImageView!!.setDotCenter(point)
                mImageView!!.setUncertaintyRadius(
                    mFloorPlan!!.getMetersToPixels() * location.getAccuracy())
                mImageView!!.postInvalidate()*/


                mImageView!!.setDotCenter(point)
                mImageView!!.setUncertaintyRadius(
                    mFloorPlan!!.getMetersToPixels() * location.getAccuracy()
                )
// ========================
// AUTO MOVE SCREEN
// ========================
                focusToUser(point)
// Refresh
                mImageView!!.postInvalidate()
            }
        }
    }

    private val mOrientationListener: IAOrientationListener = object : IAOrientationListener {
        override fun onHeadingChanged(timestamp: Long, heading: Double) {
            if (mFloorPlan != null) {
                mImageView!!.setHeading(heading - mFloorPlan!!.getBearing())
            }
        }

        override fun onOrientationChange(l: Long, doubles: DoubleArray?) {
            // No-op
        }
    }

    private val mRegionListener: IARegion.Listener = object : IARegion.Listener {
        override fun onEnterRegion(region: IARegion) {
            if (region.getType() == IARegion.TYPE_FLOOR_PLAN) {
                val id = region.getId()
                Log.d(TAG, "floorPlan changed to " + id)
                Toast.makeText(this@ImageViewActivity, id, Toast.LENGTH_SHORT).show()

                fetchFloorPlanBitmap(region.getFloorPlan())
                setupPoIs(mVenue!!.getPOIs(), region.getFloorPlan().getFloorLevel())
            } else if (region.getType() == IARegion.TYPE_VENUE) {
                mVenue = region.getVenue()
                //  setupPoIs(mVenue.getPOIs(), region.getFloorPlan().getFloorLevel());
            }
        }

        override fun onExitRegion(region: IARegion?) {
            // leaving a previously entered region
        }
    }


    private fun setWayfindingTarget(point: LatLng, addMarker: Boolean) {
        if (mFloorPlan == null) {
            Log.d(TAG, "FloorPlan not loaded yet")
            return
        }

        // =========================
        // Create Wayfinding Request
        // =========================
        mWayfindingDestination = IAWayfindingRequest.Builder()
            .withFloor(mFloor)
            .withLatitude(point.latitude)
            .withLongitude(point.longitude)
            .build()

        // =========================
        // Request Route
        // =========================
        if (mAccessibleRoute) {
            Log.d(TAG, "Requesting accessible route")
            val req =
                IAWayfindingRequest.Builder()
                    .withDestination(mWayfindingDestination)
                    .withTags(IAWayfindingTags.EXCLUDE_INACCESSIBLE)
                    .build()

            mIALocationManager?.requestWayfindingUpdates(
                req,
                mWayfindingListener
            )
        } else {
            Log.d(TAG, "Requesting default route")

            mIALocationManager?.requestWayfindingUpdates(
                mWayfindingDestination!!,
                mWayfindingListener
            )
        }

        // =========================
        // Add Destination Marker
        // =========================
        if (addMarker) {
            val iaLatLng = IALatLng(
                point.latitude,
                point.longitude
            )

            // Convert destination LatLng -> bitmap point
            val bitmapPoint =
                mFloorPlan!!.coordinateToPoint(iaLatLng)

            val markers: MutableList<BlueDotView.POIMarker?> =
                ArrayList<BlueDotView.POIMarker?>()

            markers.add(
                BlueDotView.POIMarker(
                    "Destination",
                    bitmapPoint,
                    LatLng(
                        point.latitude,
                        point.longitude
                    )
                )
            )

            mImageView!!.setPoiMarkers(markers)

            mImageView!!.invalidate()
        }

        Log.d(
            TAG,
            ("Set destination: ("
                    + mWayfindingDestination!!.getLatitude()
                    + ", "
                    + mWayfindingDestination!!.getLongitude()
                    + "), floor="
                    + mWayfindingDestination!!.getFloor())
        )
    }


   /* private val mWayfindingListener: IAWayfindingListener = object : IAWayfindingListener {
        override fun onWayfindingUpdate(route: IARoute) {
            mCurrentRoute = route
            if (hasArrivedToDestination(route)) {
                // stop wayfinding
                showInfo(this@ImageViewActivity,"You're there!")
                mCurrentRoute = null
                mWayfindingDestination = null
                mIALocationManager!!.removeWayfindingUpdates()
            }
            updateRouteVisualization()
        }
    }*/

    private val mWayfindingListener: IAWayfindingListener =
        object : IAWayfindingListener {

            override fun onWayfindingUpdate(route: IARoute) {

                mCurrentRoute = route

                if (hasArrivedToDestination(route)) {

                    // Stop wayfinding

                    showInfo(this@ImageViewActivity, "You're there!")

                    mCurrentRoute = null
                    mWayfindingDestination = null
                    mIALocationManager!!.removeWayfindingUpdates()

                    // =========================
                    // Clear Route Polyline
                    // =========================

                    mImageView!!.setRoutePoints(ArrayList())
                    // =========================
                    // Clear Destination Marker
                    // =========================

                    /*mImageView!!.setPoiMarkers(
                        ArrayList()
                    )*/
                    mImageView!!.invalidate()

                    return
                }

                updateRouteVisualization()
            }
        }


    private fun updateRouteVisualization() {

        clearRouteVisualization()

        if (mCurrentRoute == null || mFloorPlan == null) {
            return
        }

        // Route points for bitmap image
        val routePoints = mutableListOf<PointF?>()

        for (leg in mCurrentRoute!!.legs) {

            // Ignore artificial edges
            if (leg.edgeIndex == null) {
                continue
            }

            // Show only current floor route
            if (leg.begin.floor != mFloor ||
                leg.end.floor != mFloor
            ) {
                continue
            }

            // =========================
            // Begin Point
            // =========================

            val beginLatLng = IALatLng(
                leg.begin.latitude,
                leg.begin.longitude
            )

            val beginPoint = mFloorPlan?.coordinateToPoint(beginLatLng)

            // =========================
            // End Point
            // =========================

            val endLatLng = IALatLng(
                leg.end.latitude,
                leg.end.longitude)
            val endPoint = mFloorPlan?.coordinateToPoint(endLatLng)
            // Add for drawing line
            routePoints.add(beginPoint)
            routePoints.add(endPoint)
        }
        // =========================
        // Draw Route On Bitmap Image
        // =========================
        mImageView?.setRoutePoints(routePoints)
        mImageView?.invalidate()
    }



    private fun clearRouteVisualization() {
        for (pl in mPolylines) {
            pl.remove()
        }
        mPolylines.clear()
    }
    private fun hasArrivedToDestination(route: IARoute): Boolean {
        // empty routes are only returned when there is a problem, for example,
        // missing or disconnected routing graph
        if (route.getLegs().size == 0) {
            return false
        }

        val FINISH_THRESHOLD_METERS = 8.0
        var routeLength = 0.0
        for (leg in route.getLegs()) routeLength += leg.getLength()
        return routeLength < FINISH_THRESHOLD_METERS
    }

   /* private fun setupPoIs(
        pois: MutableList<IAPOI>,
        currentFloorLevel: Int
    ) {
        if (mFloorPlan == null) {
            return
        }

        val bitmapMarkers: MutableList<POIMarker?> =
            ArrayList<POIMarker?>()

        for (poi in pois) {
            if (poi.getFloor() == currentFloorLevel) {
                val latLng = IALatLng(
                    poi.getLocation().latitude,
                    poi.getLocation().longitude
                )

                // Convert LatLng -> Bitmap Point
                val point = mFloorPlan!!.coordinateToPoint(latLng)
                bitmapMarkers.add(
                    POIMarker(poi.getName(), point)
                )
            }
        }

        // Send markers to ImageView
        mImageView!!.setPoiMarkers(bitmapMarkers)
        mImageView!!.invalidate()
    }*/

    private fun setupPoIs(
        pois: MutableList<IAPOI>,
        currentFloorLevel: Int
    ) {

        if (mFloorPlan == null) {
            return
        }

        val bitmapMarkers = ArrayList<BlueDotView.POIMarker>()
        mPoIMarkers.clear()
        poiMarkerMap.clear()
        allPois.clear()
        allPois.addAll(pois)

        for (poi in pois) {
            if (poi.floor == currentFloorLevel) {

                val latLng = IALatLng(
                    poi.location.latitude,
                    poi.location.longitude)
                // Convert LatLng -> Bitmap Point
                val point = mFloorPlan!!.coordinateToPoint(latLng)
                var marker= BlueDotView.POIMarker(
                    poi.name,
                    point,
                    LatLng(
                        poi.location.latitude,
                        poi.location.longitude
                    )
                )
                bitmapMarkers.add(marker)

                if (marker != null) {
                    mPoIMarkers.add(marker)
                    poiMarkerMap[poi.name] = marker
                }
            }
        }
        // =========================
        // Set markers on ImageView
        // =========================

        mImageView!!.setPoiMarkers(bitmapMarkers)

        // =========================
        // Click Listener
        // =========================

        mImageView?.setOnPoiClickListener(
            object : BlueDotView.OnPoiClickListener {

                override fun onPoiClick(marker: BlueDotView.POIMarker) {
                    Log.d(TAG, "Clicked Marker: ${marker.name}")
                    // Start Navigation
                    setWayfindingTarget(
                        marker.latLng,
                        false
                    )
                }
            }
        )

        mImageView!!.invalidate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)
        // prevent the screen going to sleep while app is on foreground
        findViewById<View?>(android.R.id.content).setKeepScreenOn(true)

        mImageView = findViewById<BlueDotView?>(R.id.imageView)
        etSearch = findViewById(R.id.etSearch)
        listSearch = findViewById(R.id.listSearch)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listSearch.adapter = adapter
        mIALocationManager = IALocationManager.create(this)

        if (!checkLocationPermissions(this)) {
            requestPermissions(this)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }
            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                val query = s.toString().trim()

                filterPois(query)
            }

        })


        listSearch.setOnItemClickListener { _, _, position, _ ->

            val poi = filteredPois[position]

            etSearch.setText(poi.name)

            listSearch.visibility = View.GONE

            val marker = poiMarkerMap[poi.name]

            if (marker != null) {
                setWayfindingTarget(marker.latLng!!, false)

            } else {

                val latLng = LatLng(
                    poi.location.latitude,
                    poi.location.longitude
                )

                setWayfindingTarget(latLng, true)
            }
        }
        // Setup long click listener for sharing traceId
       // ExampleUtils.shareTraceId(findViewById<View?>(R.id.imageView), this@ImageViewActivity, mIALocationManager)
    }


    private fun filterPois(query: String) {

        filteredPois.clear()

        val names = ArrayList<String>()

        if (query.isEmpty()) {
            listSearch.visibility = View.GONE
            return
        }

        for (poi in allPois) {

            if (poi.name.lowercase()
                    .contains(query.lowercase())
            ) {

                filteredPois.add(poi)
                names.add(poi.name)
            }
        }

        adapter.clear()
        adapter.addAll(names)
        adapter.notifyDataSetChanged()

        listSearch.visibility =
            if (names.isEmpty()) View.GONE
            else
                View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        mIALocationManager!!.destroy()
    }

    override fun onResume() {
        super.onResume()
        // starts receiving location updates
        /**mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mLocationListener); */
        mIALocationManager!!.registerRegionListener(mRegionListener)
        val orientationRequest = IAOrientationRequest(10.0, 10.0)
        mIALocationManager!!.registerOrientationListener(orientationRequest, mOrientationListener)


        val locReq = IALocationRequest.create()
        locReq.fastestInterval = 200
        // default mode
        locReq.setPriority(IALocationRequest.PRIORITY_HIGH_ACCURACY)
        // Low power mode: Uses less power, but has lower accuracy use e.g. for background tracking
        //locReq.setPriority(IALocationRequest.PRIORITY_LOW_POWER);
        // Cart mode: Use when device is mounted to a shopping cart or similar platform with wheels
        locReq.setPriority(IALocationRequest.PRIORITY_CART_MODE);

        // --- start receiving location updates & monitor region changes
        mIALocationManager!!.requestLocationUpdates(locReq, mLocationListener)
        mIALocationManager!!.registerRegionListener(mRegionListener)
    }

    override fun onPause() {
        super.onPause()
        mIALocationManager!!.removeLocationUpdates(mLocationListener)
        mIALocationManager!!.unregisterRegionListener(mRegionListener)
        mIALocationManager!!.unregisterOrientationListener(mOrientationListener)
    }

    /**
     * Methods for fetching bitmap image.
     */
    private fun showFloorPlanImage(bitmap: Bitmap) {
        mImageView!!.setDotRadius(mFloorPlan!!.getMetersToPixels() * dotRadius)
        mImageView!!.setImage(ImageSource.cachedBitmap(bitmap))
    }


    /**
     * Download floor plan using Picasso library.
     */
    private fun fetchFloorPlanBitmap(floorPlan: IAFloorPlan) {
        mFloorPlan = floorPlan
        val url = floorPlan.getUrl()
        mLoadTarget = object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom?) {
                Log.d(
                    TAG, ("onBitmap floorplan loaded with dimensions: "
                            + bitmap.getWidth() + "x" + bitmap.getHeight())
                )
                if (mFloorPlan != null && floorPlan.getId() == mFloorPlan!!.getId()) {
                    showFloorPlanImage(bitmap)
                }
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // N/A
            }

            override fun onBitmapFailed(placeHolderDrawable: Drawable?) {
                Toast.makeText(this@ImageViewActivity, "Failed to load bitmap", Toast.LENGTH_SHORT)
                    .show()
                mFloorPlan = null
            }
        }

        val request = Picasso.with(this).load(url)
        request.into(mLoadTarget)
    }

    companion object {
        private const val TAG = "IndoorAtlasExample"

        // blue dot radius in meters
        private const val dotRadius = 1.0f
    }
    fun checkLocationPermissions(activity: Activity): Boolean {
        val locationPermission =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothScanPermission =
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

            val bluetoothConnectPermission =
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            return locationPermission
                    && bluetoothScanPermission
                    && bluetoothConnectPermission
        } else {
            return locationPermission
        }
    }
    fun requestPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var granted = true

            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false
                    break
                }
            }

            if (granted) {
               // initializeMap()
            } else {
                //showInfo(con,"Permissions denied")
            }
        }
    }

    /*private fun focusToUser(point: PointF) {

        // Smoothly animate to current position
        mImageView?.animateCenter(point)

        // Optional zoom
        if (mImageView?.scale ?: 0f < 2.0f) {
            mImageView?.setScaleAndCenter(
                2.4f,
                point
            )
        }
    }*/



    private fun focusToUser(point: PointF) {

        if (mImageView == null) return

        // Prevent tiny movements
        lastPoint?.let {

            val dx = point.x - it.x
            val dy = point.y - it.y

            val distance = kotlin.math.sqrt(
                (dx * dx + dy * dy).toDouble()
            )

            // Ignore very small updates
            if (distance < 20) {
                return
            }
        }

        lastPoint = point

        // Smooth animated move
        mImageView?.animateScaleAndCenter(
            0.3f,
            point
        )
            ?.withDuration(500)
            ?.start()
    }


}

