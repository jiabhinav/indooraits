package com.app.indooraits.wayfinding

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresPermission
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.app.indoor.imageview.Smooth
import com.app.indooraits.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
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
import com.indooratlas.android.sdk.resources.IALocationListenerSupport
import com.indooratlas.android.sdk.resources.IAVenue
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import java.util.Arrays

class WayfindingOverlayActivity : FragmentActivity(), OnMapClickListener, OnMapReadyCallback {
    private var mMap: GoogleMap? =
        null // Might be null if Google Play services APK is not available.

    private var mCircle: Circle? = null
    private var mOverlayFloorPlan: IARegion? = null
    private var mGroundOverlay: GroundOverlay? = null
    private var mIALocationManager: IALocationManager? = null
    private var mLoadTarget: com.squareup.picasso.Target? = null
    private var mCameraPositionNeedsUpdating = true // update on first location
    private var mDestinationMarker: Marker? = null
    private var mHeadingMarker: Marker? = null
    private var mVenue: IAVenue? = null
    private val mPoIMarkers: MutableList<Marker> = ArrayList<Marker>()
    private val mPolylines: MutableList<Polyline> = ArrayList<Polyline>()
    private var mCurrentRoute: IARoute? = null

    private lateinit var etSearch: EditText
    private lateinit var listSearch: ListView


    private val allPois: MutableList<IAPOI> = ArrayList()
    private val filteredPois: MutableList<IAPOI> = ArrayList()

    private lateinit var adapter: ArrayAdapter<String>


    private val poiMarkerMap = HashMap<String, Marker>()
    var floorConfidence: Float?=null
    var mCurrentFloorLevel: Int?=null

    private var mWayfindingDestination: IAWayfindingRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // prevent the screen going to sleep while app is on foreground
        findViewById<View?>(android.R.id.content).setKeepScreenOn(true)

        // instantiate IALocationManager
        mIALocationManager = IALocationManager.create(this)




        //====SEarch Feature============
        etSearch = findViewById(R.id.etSearch)
        listSearch = findViewById(R.id.listSearch)

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            ArrayList()
        )
        listSearch.adapter = adapter




        // Try to obtain the map from the SupportMapFragment.
        val mapFragment =
            getSupportFragmentManager()
                .findFragmentById(R.id.map) as SupportMapFragment?

        if (mapFragment != null) {
            mapFragment.getMapAsync(this)
        }


        // Handle "accessible route" checkbox state change
        val accessibleRouteCheckbox = findViewById<CheckBox>(R.id.checkbox_accessible_route)
        accessibleRouteCheckbox.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {

            override fun onCheckedChanged(p0: CompoundButton, isChecked: Boolean) {
                if (isChecked) {
                    // Code to handle accessible route enabled
                    showInfo("Accessible route enabled")
                    mAccessibleRoute = true
                } else {
                    // Code to handle accessible route disabled
                    showInfo("Accessible route disabled")
                    mAccessibleRoute = false
                }
            }
        })



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

                marker.showInfoWindow()
                mMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        marker.position,
                        21f
                    ))

                Log.d(TAG, "onCreate: "+marker.position)

                setWayfindingTarget(marker.position, false)

            } else {

                val latLng = LatLng(
                    poi.location.latitude,
                    poi.location.longitude
                )

                setWayfindingTarget(latLng, true)
            }
        }


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
            else View.VISIBLE
    }



    override fun onDestroy() {
        super.onDestroy()

        // remember to clean up after ourselves
        mIALocationManager!!.destroy()
    }

    override fun onResume() {
        super.onResume()

        // start receiving location updates & monitor region changes
        val locReq = IALocationRequest.create()
        // default mode
        locReq.setPriority(IALocationRequest.PRIORITY_HIGH_ACCURACY)
        locReq.setPriority(IALocationRequest.PRIORITY_CART_MODE)
        mIALocationManager!!.requestLocationUpdates(locReq, mListener)
        mIALocationManager!!.registerRegionListener(mRegionListener)

        //mIALocationManager!!.lockFloor(1)
        mIALocationManager!!.registerOrientationListener( // update if heading changes by 1 degrees or more
            IAOrientationRequest(1.0, 0.0),
            mOrientationListener
        )

        if (mWayfindingDestination != null) {
            mIALocationManager!!.requestWayfindingUpdates(
                mWayfindingDestination!!,
                mWayfindingListener
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // unregister location & region changes
        mIALocationManager!!.removeLocationUpdates(mListener)
        mIALocationManager!!.unregisterRegionListener(mRegionListener)
        mIALocationManager!!.unregisterOrientationListener(mOrientationListener)

        if (mWayfindingDestination != null) {
            mIALocationManager!!.removeWayfindingUpdates()
        }
    }


   /* private fun setupPoIs(pois: MutableList<IAPOI>, currentFloorLevel: Int) {
        Log.d(TAG, pois.size.toString() + " PoI(s)")
        allPois.clear()
        allPois.addAll(pois)
        // remove any existing markers
        for (m in mPoIMarkers) {
            m.remove()
        }
        mPoIMarkers.clear()
        for (poi in pois) {
            if (poi.getFloor() == currentFloorLevel) {
                mPoIMarkers.add(
                    mMap!!.addMarker(
                        MarkerOptions()
                            .title(poi.getName())
                            .position(
                                com.google.android.gms.maps.model.LatLng(
                                    poi.getLocation().latitude,
                                    poi.getLocation().longitude
                                )
                            )
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )!!
                )
            }


        }




    }
*/

    private fun setupPoIs(
        pois: MutableList<IAPOI>,
        currentFloorLevel: Int
    ) {

        Log.d(TAG, pois.size.toString() + " PoI(s)")

        for (m in mPoIMarkers) {
            m.remove()
        }

        mPoIMarkers.clear()
        poiMarkerMap.clear()

        allPois.clear()
        allPois.addAll(pois)

        for (poi in pois) {

            if (poi.floor == currentFloorLevel) {

                val marker = mMap!!.addMarker(
                    MarkerOptions()
                        .title(poi.name)
                        .position(
                            LatLng(
                                poi.location.latitude,
                                poi.location.longitude
                            )
                        )
                        .icon(
                            BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_BLUE
                            )
                        )
                )

                if (marker != null) {
                    mPoIMarkers.add(marker)
                    poiMarkerMap[poi.name] = marker
                }
            }
        }
    }

    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private fun setupGroundOverlay(floorPlan: IAFloorPlan, bitmap: Bitmap) {
        if (mGroundOverlay != null) {
            mGroundOverlay!!.remove()
        }

        if (mMap != null) {
            val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
            val iaLatLng = floorPlan.getCenter()
            val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
            val fpOverlay = GroundOverlayOptions()
                .image(bitmapDescriptor)
                .zIndex(0.0f)
                .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                .bearing(floorPlan.getBearing())

            mGroundOverlay = mMap!!.addGroundOverlay(fpOverlay)
        }
    }


    /**
     * Download floor plan using Picasso library.
     */
    private fun fetchFloorPlanBitmap(floorPlan: IAFloorPlan?) {
        if (floorPlan == null) {
            Log.e(TAG, "null floor plan in fetchFloorPlanBitmap")
            return
        }

        val url = floorPlan.getUrl()
        Log.d(TAG, "loading floor plan bitmap from " + url)

        mLoadTarget = object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom?) {
                Log.d(
                    TAG, ("onBitmap loaded with dimensions: " + bitmap.getWidth() + "x"
                            + bitmap.getHeight())
                )
                if (mOverlayFloorPlan != null && floorPlan.getId() == mOverlayFloorPlan!!.getId()) {
                    Log.d(TAG, "showing overlay")
                    setupGroundOverlay(floorPlan, bitmap)
                }
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // N/A
            }

            override fun onBitmapFailed(placeHolderDrawable: Drawable?) {
                showInfo("Failed to load bitmap")
                mOverlayFloorPlan = null
            }
        }

        val request = Picasso.with(this).load(url)

        val bitmapWidth = floorPlan.getBitmapWidth()
        val bitmapHeight = floorPlan.getBitmapHeight()

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION)
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0)
        }

        request.into(mLoadTarget)
    }

    private fun showInfo(text: String) {
        val snackbar = Snackbar.make(
            findViewById<View?>(android.R.id.content), text,
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction("Close", object : View.OnClickListener {
            override fun onClick(view: View?) {
                snackbar.dismiss()
            }
        })
        snackbar.show()
    }

    override fun onMapClick(point: LatLng) {
        if (mPoIMarkers.isEmpty()) {
            // if PoIs exist, only allow wayfinding to PoI markers
            Log.d(TAG, "onMapClick: "+point.toString())
            setWayfindingTarget(point, true)
        }
    }

    private fun setWayfindingTarget(point: LatLng, addMarker: Boolean) {
        if (mMap == null) {
            Log.d("Analysis__", "map not loaded yet")
            return
        }
        mWayfindingDestination = IAWayfindingRequest.Builder()
            .withFloor(mFloor)
            .withLatitude(point.latitude)
            .withLongitude(point.longitude)
            .build()

        if (mAccessibleRoute) {
            Log.d(TAG, "Requesting wayfinding with accessible route")

            val req = IAWayfindingRequest.Builder()
                .withDestination(mWayfindingDestination)
                .withTags(IAWayfindingTags.EXCLUDE_INACCESSIBLE)
                .build()
            mIALocationManager!!.requestWayfindingUpdates(req, mWayfindingListener)
        } else {
            Log.d(TAG, "Requesting wayfinding with default route")
            mIALocationManager!!.requestWayfindingUpdates(
                mWayfindingDestination!!,
                mWayfindingListener
            )
        }

        if (mDestinationMarker != null) {
            mDestinationMarker!!.remove()
            mDestinationMarker = null
        }

        if (addMarker) {
            mDestinationMarker = mMap!!.addMarker(
                MarkerOptions()
                    .position(point)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        }
        Log.d(
            TAG, "Set destination: (" + mWayfindingDestination!!.getLatitude() + ", " +
                    mWayfindingDestination!!.getLongitude() + "), floor=" +
                    mWayfindingDestination!!.getFloor()
        )
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

    /**
     * Clear the visualizations for the wayfinding paths
     */
    private fun clearRouteVisualization() {
        for (pl in mPolylines) {
            pl.remove()
        }
        mPolylines.clear()
    }

    /**
     * Visualize the IndoorAtlas Wayfinding route on top of the Google Maps.
     */
    private fun updateRouteVisualization() {
        clearRouteVisualization()

        if (mCurrentRoute == null) {
            return
        }

        for (leg in mCurrentRoute!!.getLegs()) {
            if (leg.getEdgeIndex() == null) {
                // Legs without an edge index are, in practice, the last and first legs of the
                // route. They connect the destination or current location to the routing graph.
                // All other legs travel along the edges of the routing graph.

                // Omitting these "artificial edges" in visualization can improve the aesthetics
                // of the route. Alternatively, they could be visualized with dashed lines.

                continue
            }

            val opt = PolylineOptions()
            opt.add(LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()))
            opt.add(LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()))

            // Here wayfinding path in different floor than current location is visualized in
            // a semi-transparent color
            if (leg.getBegin().getFloor() == mFloor && leg.getEnd().getFloor() == mFloor) {
                opt.color(-0xffff01)
            } else {
                opt.color(0x300000FF)
            }

            mPolylines.add(mMap!!.addPolyline(opt))
        }
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

    /**
     * Checks that we have access to required information, if not ask for users permission.
     */
    private fun ensurePermissions() {
        val needBLEScanPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val permissions: MutableList<String?> = ArrayList<String?>(
            Arrays.asList<String?>(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        if (needBLEScanPermission) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (!checkLocationPermissions(this)) {
            // We don't have access to ACCESS_FINE_LOCATION and/or BLUETOOTH_SCAN permissions
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ||
                (needBLEScanPermission && ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ))
            ) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.location_permission_request_title)
                    .setMessage(R.string.location_permission_request_rationale)
                    .setPositiveButton(
                        R.string.permission_button_accept,
                        object : DialogInterface.OnClickListener {
                            override fun onClick(dialog: DialogInterface?, which: Int) {
                                Log.d(TAG, "request permissions")
                                ActivityCompat.requestPermissions(
                                    this@WayfindingOverlayActivity,
                                    permissions.toTypedArray(),
                                    REQUEST_CODE_PERMISSIONS
                                )
                            }
                        })
                    .setNegativeButton(
                        R.string.permission_button_deny,
                        object : DialogInterface.OnClickListener {
                            override fun onClick(dialog: DialogInterface?, which: Int) {
                                Toast.makeText(
                                    this@WayfindingOverlayActivity,
                                    R.string.location_permission_denied_message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        })
                    .show()
            } else {
                // ask user for permissions
                ActivityCompat.requestPermissions(
                    this, permissions.toTypedArray(),
                    REQUEST_CODE_PERMISSIONS
                )
            }
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



    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        /*mMap!!.getUiSettings().setZoomControlsEnabled(true)
        mMap!!.getUiSettings().setCompassEnabled(true)
        mMap!!.getUiSettings().setIndoorLevelPickerEnabled(false)*/

        mMap?.getUiSettings()?.setZoomControlsEnabled(false)
        mMap?.getUiSettings()?.setCompassEnabled(true);
        mMap?.getUiSettings()?.setIndoorLevelPickerEnabled(false);
        mMap?.getUiSettings()?.setRotateGesturesEnabled(true);
        mMap?.getUiSettings()?.setTiltGesturesEnabled(true);



        mMap!!.setMinZoomPreference(30f)
        mMap!!.setMaxZoomPreference(48f)


        Log.d("Analysis__", "mMap in yet")
        if (!checkLocationPermissions(this)) {
            requestPermissions(this)
        }
        // do not show Google's outdoor location
        mMap!!.setMyLocationEnabled(false)
        // disable 3d building shapes, since they cause gray shades over floor plan images
        mMap!!.setBuildingsEnabled(false)

        mMap!!.setOnMapClickListener(this)

        // disable various Google maps UI elements that do not work indoors
        mMap!!.getUiSettings().setMapToolbarEnabled(false)

        mMap!!.setOnMarkerClickListener(object : OnMarkerClickListener {
            override fun onMarkerClick(marker: Marker): Boolean {
                // ignore clicks to artificial wayfinding target markers
                if (marker === mDestinationMarker) return false

                Log.d(TAG, "setOnMarkerClickListener: "+marker.getPosition().toString())
                setWayfindingTarget(marker.getPosition(), false)
                // do not consume the event so that the popup with marker name is displayed
                return false
            }
        })
        //mMap?.mapType = GoogleMap.MAP_TYPE_NONE
    }


    private val mWayfindingListener: IAWayfindingListener = object : IAWayfindingListener {
        override fun onWayfindingUpdate(route: IARoute) {
            mCurrentRoute = route
            if (hasArrivedToDestination(route)) {
                // stop wayfinding
                showInfo("You're there!")
                mCurrentRoute = null
                mWayfindingDestination = null
                mIALocationManager!!.removeWayfindingUpdates()
            }
            updateRouteVisualization()
        }
    }

    private fun initializeMap() {
        mIALocationManager = IALocationManager.create(this)

        val mapFragment =
            getSupportFragmentManager()
                .findFragmentById(R.id.map) as SupportMapFragment?

        if (mapFragment != null) {
            mapFragment.getMapAsync(this)
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
                initializeMap()
            } else {
                showInfo("Permissions denied")
            }
        }
    }

    private val mOrientationListener: IAOrientationListener = object : IAOrientationListener {
        override fun onHeadingChanged(timestamp: Long, heading: Double) {
            updateHeading(heading)
        }

        override fun onOrientationChange(timestamp: Long, quaternion: DoubleArray?) {
            // we do not need full device orientation in this example, just the heading
        }
    }

    private var mFloor = 0
    private var mAccessibleRoute = true


    //commented
    private fun showLocationCircle(center: LatLng, accuracyRadius: Double) {
        if (mCircle == null) {
            // location can received before map is initialized, ignoring those updates
            if (mMap != null) {
                mCircle = mMap!!.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(accuracyRadius)
                        .fillColor(0x201681FB)
                        .strokeColor(0x500A78DD)
                        .zIndex(1.0f)
                        .visible(true)
                        .strokeWidth(5.0f)
                )
                mHeadingMarker = mMap!!.addMarker(
                    MarkerOptions()
                        .position(center)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.map_blue_dot))
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )
            }
        } else {
            // move existing markers position to received location
            mCircle!!.setCenter(center)
            mHeadingMarker!!.setPosition(center)
            mCircle!!.setRadius(accuracyRadius)
        }
    }

/*    private fun showLocationCircle(center: LatLng, accuracyRadius: Double) {

        // reduce circle size
        val fixedRadius = accuracyRadius.coerceAtMost(2.0)

        if (mCircle == null) {

            if (mMap != null) {

                mCircle = mMap!!.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(fixedRadius)
                        .fillColor(0x201681FB)
                        .strokeColor(0x500A78DD)
                        .zIndex(1.0f)
                        .visible(true)
                        .strokeWidth(2.0f)
                )

                mHeadingMarker = mMap!!.addMarker(
                    MarkerOptions()
                        .position(center)
                        .icon(
                            BitmapDescriptorFactory.fromResource(
                                R.drawable.map_blue_dot
                            )
                        )
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )
            }

        } else {

            mCircle!!.center = center
            mHeadingMarker!!.position = center

            // update reduced radius
            mCircle!!.radius = fixedRadius
        }
    }*/

    private fun updateHeading(heading: Double) {
        if (mHeadingMarker != null) {
            mHeadingMarker!!.setRotation(heading.toFloat())
        }
    }

    /**
     * Listener that handles location change events.
     */
    private val mListener: IALocationListener = object : IALocationListenerSupport() {
        /**
         * Location changed, move marker and camera position.
         */
        override fun onLocationChanged(location: IALocation) {
            Log.d(
                TAG, ("new location re" + "ceived with coordinates: " + location.getLatitude()
                        + "," + location.getLongitude())
            )

            if (mMap == null) {
                // location received before map is initialized, ignoring update here
                return
            }

            val center = LatLng(location.getLatitude(), location.getLongitude())

            val newFloor = location.getFloorLevel()
             mCurrentFloorLevel = if (location.hasFloorLevel()) location.getFloorLevel() else null
           // floorConfidence = if (location.hasFloorCertainty()) location.floorCertainty else null
            findViewById<TextView>(R.id.conf).text=location.floorCertainty.toString()
            Log.d("floorConfidence", "onLocationChanged: ${location.hasFloorCertainty()}")
           // floorConfidence = if (location.hasFloorCertainty()) location.floorCertainty else null
            floorConfidence = if (location.floorCertainty>0.75) location.floorCertainty else null
            Log.d("floorConfidence", "onLocationChanged: ${floorConfidence}")
            if (floorConfidence != null && mCurrentFloorLevel!=null ) {

            }
            if (mFloor != newFloor) {
                updateRouteVisualization()

            }
            mFloor = newFloor
            showLocationCircle(center, location.getAccuracy().toDouble())
            // our camera position needs updating if location has significantly changed
            if (mCameraPositionNeedsUpdating) {
                mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 19f))
                mCameraPositionNeedsUpdating = true
            }

        }
    }

    /**
     * Listener that changes overlay if needed
     */
    private val mRegionListener: IARegion.Listener = object : IARegion.Listener {
        override fun onEnterRegion(region: IARegion) {
            Toast.makeText(this@WayfindingOverlayActivity, "Enter Floor", Toast.LENGTH_SHORT).show()
            if (region.getType() == IARegion.TYPE_FLOOR_PLAN) {
                    Log.d(TAG, "enter floor plan " + region.name)
                    findViewById<Button>(R.id.btnFloor).text=region.name
                    mCameraPositionNeedsUpdating = true // entering new fp, need to move camera
                    if (mGroundOverlay != null) {
                        mGroundOverlay!!.remove()
                        mGroundOverlay = null
                    }
                    mOverlayFloorPlan = region // overlay will be this (unless error in loading)
                Log.d("floorConfidence", "enter floor plan " + floorConfidence)
                    if (floorConfidence!=null)
                    {
                        fetchFloorPlanBitmap(region.getFloorPlan())
                        setupPoIs(mVenue!!.getPOIs(), region.getFloorPlan().getFloorLevel())

                    }

            } else if (region.getType() == IARegion.TYPE_VENUE) {
                mVenue = region.getVenue()
            }
        }

        override fun onExitRegion(region: IARegion?) {
            Toast.makeText(this@WayfindingOverlayActivity, "Exit Floor", Toast.LENGTH_SHORT).show()
        }
    }





    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val TAG = "IndoorAtlasExample"

        /* used to decide when bitmap should be downscaled */
        private const val MAX_DIMENSION = 2048
    }
}
