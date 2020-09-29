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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RocksViewModel : ViewModel() {
    companion object {
        private const val TAG = "RocksViewModel"
        private const val DEFAULT_ZOOM = 12f
        private const val MAX_ZOOM = 14.9f
    }

    val selectedPackName = MutableLiveData<String>("")
    val sceneUpdatesForSelectedPack: LiveData<List<SceneUpdate>> = Transformations.map(selectedPackName) { packName ->
        listOf(
            SceneUpdate(
                "sources.underfoot.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/rocks.mbtiles"
            ),
            SceneUpdate(
                "sources.underfoot_ways.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/ways.mbtiles"
            ),
            SceneUpdate(
                "sources.underfoot_elevation.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/contours.mbtiles"
            )
        )
    }

    val sceneFilePath = MutableLiveData<String>(RocksMapResponder.SCENE_FILE_PATH)

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

    // Update to the camera, to be executed in an observer
    val cameraUpdate = MutableLiveData<CameraUpdate>()

    // Where to start the camera
    val initialCameraUpdate = MutableLiveData<CameraUpdate>()

    val feature = MutableLiveData<FeaturePickResult>()
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

    // Captures the last intention of the user regarding the visibility of the feature detail view.
    // Does not contain the current visibility state of the view, just the last time the user
    // explicitly expressed an action to show or hide it
    val lastRequestedDetailState = MutableLiveData<Boolean>(false)
    fun showDetailPanel() {
        lastRequestedDetailState.value = true
    }


    lateinit var locationManager: LocationManager
    val userLocation = MutableLiveData<Location>()
    val trackingUserLocation = MutableLiveData<Boolean>(false)
    val requestingLocationUpdates = MutableLiveData<Boolean>(false)

    private val locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            val loc: Location = location ?: return
            val isBetter = MapHelpers.isBetterLocation(loc, userLocation.value)
            if (isBetter && loc.accuracy < 100) {
                userLocation.value = loc
                if (trackingUserLocation.value == true) {
                    cameraUpdate.value = CameraUpdateFactory.newLngLatZoom(
                        LngLat(loc.longitude, loc.latitude),
                        cameraPosition.value?.zoom ?: DEFAULT_ZOOM
                    )
                }
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String?) {}
        override fun onProviderDisabled(provider: String?) {}
    }

    fun panToLocation(lngLat: LngLat, zoom: Float?, manual: Boolean = false) {
        val z = if (manual) {
            zoom ?: DEFAULT_ZOOM
        } else {
            if (zoom == null) {
                DEFAULT_ZOOM
            } else {
                min(max(zoom, DEFAULT_ZOOM), MAX_ZOOM)
            }

        }
        cameraUpdate.value = CameraUpdateFactory.newLngLatZoom(lngLat, z)
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
        userLocation.value?.let {location ->
            cameraUpdate.value = CameraUpdateFactory.newLngLatZoom(
                LngLat(location.longitude, location.latitude),
                cameraPosition.value?.zoom ?: DEFAULT_ZOOM
            )
        }
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
}
