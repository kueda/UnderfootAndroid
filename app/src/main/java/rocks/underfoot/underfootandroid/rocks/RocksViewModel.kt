package rocks.underfoot.underfootandroid.rocks

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.mapzen.tangram.*
import kotlin.math.roundToInt

class RocksViewModel : ViewModel(),
    MapController.SceneLoadListener,
    TouchInput.DoubleTapResponder,
    MapChangeListener,
    FeaturePickListener
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

    private var feature = MutableLiveData<FeaturePickResult>()
    val featureTitle: LiveData<String> = Transformations.map(feature) {f ->
        featurePropertyString("title", f.properties)
    }
    val featureLithology: LiveData<String> = Transformations.map(feature) {f ->
        featurePropertyString("lithology", f.properties, default="Lithology: Unknown")
    }
    val featureDescription: LiveData<String> = Transformations.map(feature) {f ->
        featurePropertyString("description", f.properties)
    }
    val featureAge: LiveData<String> = Transformations.map(feature) {f ->
        val estAge = humanizeAge(f.properties["est_age"]);
        val span = featurePropertyString("span", f.properties)
            .capitalize().replace(" To ", " to ")
        "Age: $span ($estAge)"
    }
    val featureEstAge: LiveData<String> = Transformations.map(feature) {f ->
        val span = featurePropertyString("span", f.properties)
            .capitalize().replace(" To ", " to ")
        "$span (${humanizeAge(f.properties["max_age"])} - ${humanizeAge(f.properties["min_age"])})"
    }
    val featureSource: LiveData<String> = Transformations.map(feature) {f ->
        featurePropertyString("source", f.properties)
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
            if (it.length > 0 ) return it
        }
        return default
    }

    // Note that this isn't an override, it's just a way for the fragment to assign the
    // MapController to the view model and perform the associated setup. It's not clear to me if
    // the MapController contains references to views and will thus violate separation a view model
    // is supposed to establish
    fun onMapReady(mc: MapController) {
        mapController = mc
        mapController.let {
            it.setSceneLoadListener(this)
            it.loadSceneFile(SCENE_FILE_PATH)
            it.setMapChangeListener(this)
            it.setFeaturePickListener(this)
            it.touchInput.let {ti ->
//            ti.setTapResponder(this);
                ti.setDoubleTapResponder(this);
                ti.setRotateResponder(object: TouchInput.RotateResponder {
                    // Disable rotation
                    override fun onRotateBegin(): Boolean { return true }
                    override fun onRotate(x: Float, y: Float, rotation: Float): Boolean { return true }
                    override fun onRotateEnd(): Boolean { return true }
                });
                ti.setShoveResponder(object: TouchInput.ShoveResponder {
                    // Disable perspective changes, though dang, how cool would it be to have a 3D DEM
                    override fun onShoveBegin(): Boolean { return false }
                    override fun onShove(distance: Float): Boolean { return true }
                    override fun onShoveEnd(): Boolean { return false }
                })
            }
        }
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

    // Zoom on double tap
    override fun onDoubleTap(x: Float, y: Float): Boolean {
        val newZoom = mapController.cameraPosition.zoom + 1F;
        val tapped = mapController.screenPositionToLngLat(PointF(x, y));
        tapped?.let {
            mapController.updateCameraPosition(CameraUpdateFactory.newLngLatZoom(it, newZoom), 500);
        }
        return true;
    }

    // MapChangeListener ovverrides
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
        val center = mapController.lngLatToScreenPosition(p.position)
        Log.d(TAG, "center: $center")
        mapController.pickFeature(center.x, center.y)
    }

    override fun onFeaturePickComplete(result: FeaturePickResult?) {
        Log.d(TAG, "onFeaturePickComplete, result: $result")
        if (result == null) { return }
        feature.value = result
    }
}
