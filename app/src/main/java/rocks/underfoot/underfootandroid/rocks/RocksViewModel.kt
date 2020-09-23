package rocks.underfoot.underfootandroid.rocks

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.mapzen.tangram.*
import rocks.underfoot.underfootandroid.maptuils.*
import kotlin.math.roundToInt

class RocksViewModel : ViewModel(),
    MapController.SceneLoadListener,
    MapCenterTrackable,
    FeatureChoosable,
    TapToPanable,
    DoubleTapToPanAndZoomable,
    Unrotatable,
    Unshovable
{
    private val TAG = "RocksViewModel"
    private val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
    private val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
    private val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"

    lateinit var mapController: MapController

    val selectedPackName = MutableLiveData<String>("")

    override val lat = MutableLiveData<Double>(37.73)
    val latString: LiveData<String> = Transformations.map(lat) { l -> "%.2f".format(l) }

    override val lng = MutableLiveData<Double>(-122.24)
    val lngString: LiveData<String> = Transformations.map(lng) { l -> "%.2f".format(l) }

    override val zoom = MutableLiveData<Float>(10.0F)
    val zoomString: LiveData<String> = Transformations.map(zoom) { z -> "%.1f".format(z) }

    override var feature = MutableLiveData<FeaturePickResult>()
    val featureTitle: LiveData<String> = Transformations.map(feature) { f ->
        featurePropertyString("title", f.properties)
    }
    val featureLithology: LiveData<String> = Transformations.map(feature) { f ->
        featurePropertyString("lithology", f.properties, default = "Lithology: Unknown")
    }
    val featureDescription: LiveData<String> = Transformations.map(feature) { f ->
        featurePropertyString("description", f.properties)
    }
    val featureAge: LiveData<String> = Transformations.map(feature) { f ->
        val estAge = humanizeAge(f.properties["est_age"]);
        val span = featurePropertyString("span", f.properties)
            .capitalize().replace(" To ", " to ")
        "Age: $span ($estAge)"
    }
    val featureEstAge: LiveData<String> = Transformations.map(feature) { f ->
        val span = featurePropertyString("span", f.properties)
            .capitalize().replace(" To ", " to ")
        "$span (${humanizeAge(f.properties["max_age"])} - ${humanizeAge(f.properties["min_age"])})"
    }
    val featureSource: LiveData<String> = Transformations.map(feature) { f ->
        featurePropertyString("source", f.properties)
    }

    lateinit var locationManager: LocationManager
    var userLocation: Location? = null
    override val trackingUserLocation = MutableLiveData<Boolean>(false)
    val requestingLocationUpdates = MutableLiveData<Boolean>(false)

    private val locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            Log.d(TAG, "onLocationChanged, location: $location")
            location ?: return
            val isBetter = MapHelpers.isBetterLocation(location, userLocation)
            if (isBetter && location.accuracy < 100) {
                userLocation = location
//                showCurrentLocation()
                if (trackingUserLocation.value == true) {
                    panToCurrentLocation()
                }
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String?) {}
        override fun onProviderDisabled(provider: String?) {}
    }

    fun panToCurrentLocation() {
        userLocation?.let {
            val lngLat = LngLat(it.longitude, it.latitude)
            val z = if (zoom.value!! < 10) 10f else zoom.value
            panToLocation(lngLat, zoom = z, manual = false)
        }
    }

    override fun panToLocation(lngLat: LngLat, zoom: Float?, manual: Boolean) {
        val cameraUpdate = if (zoom == null) {
            CameraUpdateFactory.setPosition(lngLat)
        } else {
            CameraUpdateFactory.newLngLatZoom(lngLat, zoom)
        }
        mapController.updateCameraPosition(cameraUpdate, 500)
        if (manual) {
            trackingUserLocation.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startGettingLocation() {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000,
            10f,
            locationListener
        )
        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            5000,
            10f,
            locationListener
        )

    }

    fun requestLocationUpdates() {
        requestingLocationUpdates.value = true
        trackingUserLocation.value = true
    }

    private fun humanizeAge(ageArg: String?): String {
        val default = "?"
        val age = if (ageArg.isNullOrEmpty()) return default else ageArg
        val ageNum = age.toFloatOrNull() ?: return default
        return when {
            ageNum >= 1000000000 -> "%.1f Ga".format(ageNum / 1000000000.0)
            ageNum >= 1000000 -> "%.1f Ma".format(ageNum / 1000000.0)
            ageNum >= 100000 -> "%.1f ka".format(ageNum / 1000.0)
            else -> "%,d years".format(ageNum.roundToInt())
        }
    }

    private fun featurePropertyString(
        propName: String,
        properties: Map<String, String>,
        default: String = "Unknown"
    ): String {
        if (properties.isEmpty()) return default
        properties[propName]?.let {
            if (it.isNotEmpty()) return it
        }
        return default
    }

    // Note that this isn't an override, it's just a way for the fragment to assign the
    // MapController to the view model and perform the associated setup. It's not clear to me if
    // the MapController contains references to views and will thus violate separation a view model
    // is supposed to establish
    override fun onMapReady(mc: MapController) {
        super<MapCenterTrackable>.onMapReady(mc)
        super<FeatureChoosable>.onMapReady(mc)
        super<TapToPanable>.onMapReady(mc)
        super<DoubleTapToPanAndZoomable>.onMapReady(mc)
        super<Unrotatable>.onMapReady(mc)
        super<Unshovable>.onMapReady(mc)
        mapController = mc
        mapController.touchInput?.let {touchInput ->
            val defaultPanResponder = mapController.panResponder
            touchInput.setPanResponder(object : TouchInput.PanResponder by defaultPanResponder {
                override fun onPanEnd(): Boolean {
                    // custom thing
                    trackingUserLocation.value = false
                    return defaultPanResponder.onPanEnd()
                }
            })
        }
        mapController.setSceneLoadListener(this)
        mapController.loadSceneFile(
            SCENE_FILE_PATH, listOf(
                SceneUpdate(
                    "sources.underfoot.url",
                    "file:///data/user/0/rocks.underfoot.underfootandroid/files/${selectedPackName.value}/rocks.mbtiles"
                ),
                SceneUpdate(
                    "sources.underfoot_ways.url",
                    "file:///data/user/0/rocks.underfoot.underfootandroid/files/${selectedPackName.value}/ways.mbtiles"
                ),
                SceneUpdate(
                    "sources.underfoot_elevation.url",
                    "file:///data/user/0/rocks.underfoot.underfootandroid/files/${selectedPackName.value}/contours.mbtiles"
                )
            )
        )
    }

    // Tangram overrides
    override fun onSceneReady(sceneId: Int, sceneError: SceneError?) {
        if (sceneError != null) {
            Log.d(TAG, "Scene update errors ${sceneError.sceneUpdate} ${sceneError.error}")
            return
        }
        val pos = LngLat(lng.value!!, lat.value!!);
        panToLocation(pos, zoom.value!!, false)
    }
}
