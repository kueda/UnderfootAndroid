package rocks.underfoot.underfootandroid.rocks

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.mapzen.tangram.*

class RocksViewModel : ViewModel(),
    MapController.SceneLoadListener
{
    private val TAG = "RocksViewModel"
    private val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
    private val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
    private val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"
    private val FILES = listOf(
        // From https://github.com/kueda/underfoot
        // "underfoot_units-20191124.mbtiles",
        // "underfoot_ways-20190912.mbtiles",
        // "elevation-20190408.mbtiles"
        "rocks-20200509.mbtiles",
        "ways-20200509.mbtiles",
        "contours-20200509.mbtiles"

        // // small one for download testing
        // "underfoot-20180401-14.mbtiles"
    )

    private lateinit var mapController: MapController

    private val lat = MutableLiveData<Double>(37.73)
    val latString: LiveData<String> = Transformations.map(lat) { l -> "%.2f".format(l) }

    private val lng = MutableLiveData<Double>(-122.24)
    val lngString: LiveData<String> = Transformations.map(lng) { l -> "%.2f".format(l) }

    private val zoom = MutableLiveData<Float>(10.0F)
    val zoomString: LiveData<String> = Transformations.map(zoom) {z -> "%.1f".format(z) }

    // Note that in Java it seems like you can instantiate an interface like new MapChangeListener,
    // but not in Kotlin. In the latter, this syntax works, though I'm not sure if it's conventional
    private val mapChangeListener = object: MapChangeListener {
        // Not sure why it thinks these need to be here even if nothing happens
        override fun onViewComplete() {}

        override fun onRegionWillChange(animated: Boolean) {}

        override fun onRegionIsChanging() {
            // update the map metadata as you move the map
            val p = mapController.cameraPosition
            zoom.value = p.zoom
            lat.value = p.position.latitude
            lng.value = p.position.longitude
        }

        override fun onRegionDidChange(animated: Boolean) {
            // update the map metadata when done moving, including on initial load
            val p = mapController.cameraPosition
            zoom.value = p.zoom
            lat.value = p.position.latitude
            lng.value = p.position.longitude
        }
    }

    // Note that this isn't an override, it's just a way for the fragment to assign the
    // MapController to the view model and perform the associated setup. It's not clear to me if
    // the MapController contains references to views and will thus violate separation a view model
    // is supposed to establish
    fun onMapReady(mc: MapController) {
        mapController = mc
        mapController.setSceneLoadListener(this)
        mapController.loadSceneFile(SCENE_FILE_PATH)
        mapController.setMapChangeListener(mapChangeListener)
    }

    // Tangram overrides
    override fun onSceneReady(sceneId: Int, sceneError: SceneError?) {
        if (sceneError != null) {
            Log.d(TAG, "Scene update errors ${sceneError.sceneUpdate} ${sceneError.error}")
            return
        }
        val pos = LngLat(lng.value!!, lat.value!!);
        mapController.updateCameraPosition(
            CameraUpdateFactory.newLngLatZoom(pos, zoom.value!!), 500)
    }
}