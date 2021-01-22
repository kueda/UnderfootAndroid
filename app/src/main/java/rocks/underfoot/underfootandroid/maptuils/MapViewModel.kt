package rocks.underfoot.underfootandroid.maptuils

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.mapzen.tangram.*
import kotlin.math.max
import kotlin.math.min

abstract class MapViewModel : ViewModel() {
    companion object {
        const val DEFAULT_ZOOM = 12f
        const val MAX_ZOOM = 14.9f
    }

    val selectedPackName = MutableLiveData<String>("")
    abstract val sceneUpdatesForSelectedPack: LiveData<List<SceneUpdate>>
    abstract val sceneFilePath: MutableLiveData<String>
    // Current position of the map's camera
    val cameraPosition = MutableLiveData<CameraPosition>()
    val latString: LiveData<String> = Transformations.map(cameraPosition) { cp ->
        "%.2f".format(cp.latitude)
    }
    val lngString: LiveData<String> = Transformations.map(cameraPosition) { cp ->
        "%.2f".format(cp.longitude)
    }
    val zoomString: LiveData<String> = Transformations.map(cameraPosition) { cp ->
        "%.1f".format(cp.zoom)
    }

    // Update to the camera, to be executed in an observer with no animation
    val cameraUpdate = MutableLiveData<CameraUpdate>()

    // Update to the camera, to be executed in an observer with animation. Animation doesn't
    // always trigger onRegionDidChange when the animation is done, which means you can get into
    // situations where you think the region changed and you retrieve the current map center, but
    // in reality it's still going to change some more before settling. To be sure that the region
    // changes and onRegionDidChange fires when the change is complete, set cameraUpdate for an
    // un-animated change, e.g. when initially setting the map position.
    val animatedCameraUpdate = MutableLiveData<CameraUpdate>()

    // Where to start the camera
    val initialCameraUpdate = MutableLiveData<CameraUpdate>()

    // Captures the last intention of the user regarding the visibility of the feature detail view.
    // Does not contain the current visibility state of the view, just the last time the user
    // explicitly expressed an action to show or hide it
    val lastRequestedDetailState = MutableLiveData<Boolean>(false)
    fun showDetailPanel() {
        lastRequestedDetailState.value = true
    }
    val detailShown = MutableLiveData<Boolean>(false)
    // Meant to work with a databinding attribute. Note that to get this to work I had to make this
    // return a lambda and not just refer to this function itself, and the lambda has to take a
    // view as the first arg
    fun onDetailChange(): (view: Object, shown: Boolean) -> Unit  = { _, shown ->
        run { detailShown.value = shown }
    }


    lateinit var locationManager: LocationManager
    val userLocation = MutableLiveData<Location>()
    val trackingUserLocation = MutableLiveData<Boolean>(false)
    val requestingLocationUpdates = MutableLiveData<Boolean>(false)
    val waitingForUserLocation = MutableLiveData<Boolean>(false)

    private val locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            val loc: Location = location ?: return
            val isBetter = MapHelpers.isBetterLocation(loc, userLocation.value)
            if (isBetter && loc.accuracy < 1000) {
                userLocation.value = loc
                if (trackingUserLocation.value == true) {
                    panToLocation(LngLat(loc.longitude, loc.latitude), cameraPosition.value?.zoom)
                    if (loc.accuracy < 100) {
                        waitingForUserLocation.value = false
                    }
                }
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String?) {}
        override fun onProviderDisabled(provider: String?) {}
    }

    fun panToLocation(lngLat: LngLat, zoom: Float?, manual: Boolean = false, animated: Boolean = false) {
        val z = if (manual) {
            max(zoom ?: DEFAULT_ZOOM, DEFAULT_ZOOM)
        } else {
            if (zoom == null) {
                DEFAULT_ZOOM
            } else {
                min(max(zoom, DEFAULT_ZOOM), MAX_ZOOM)
            }

        }
        val newCameraUpdate = CameraUpdateFactory.newLngLatZoom(lngLat, z)
        if (manual || animated) {
            animatedCameraUpdate.value = newCameraUpdate
            if (manual) {
                stopTrackingUserLocation()
            }
        } else {
            cameraUpdate.value = newCameraUpdate
        }
    }

    fun panToCurrentLocation() {
        startTrackingUserLocation()
        requestLocationUpdates()
        userLocation.value?.let {location ->
            panToLocation(
                LngLat(location.longitude, location.latitude),
                max(cameraPosition.value?.zoom ?: DEFAULT_ZOOM, DEFAULT_ZOOM),
                animated = true
            )
        }
    }

    fun startTrackingUserLocation() {
        trackingUserLocation.value = true
        waitingForUserLocation.value = userLocation.value == null
    }

    fun stopTrackingUserLocation() {
        trackingUserLocation.value = false
        waitingForUserLocation.value = false
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

    private fun requestLocationUpdates() {
        requestingLocationUpdates.value = true
    }
}