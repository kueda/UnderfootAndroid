package rocks.underfoot.underfootandroid.rocks

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.LifecycleOwner
import com.mapzen.tangram.*
import rocks.underfoot.underfootandroid.maptuils.MapResponder

// Responds to map events to update the view model, and observes the view model to control the map.
// Might be appropriate to call it a MapPresenter?
class RocksMapResponder(
    private val viewModel: RocksViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private var colorAccent: Int = Color.CYAN
) : MapResponder(viewModel, viewLifecycleOwner, colorAccent) {
    companion object {
        private const val TAG = "RocksMapResponder"
        const val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
        const val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
        const val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"
    }

    override fun onMapReady(mc: MapController?) {
        super.onMapReady(mc)
        mapController.setFeaturePickListener { feature -> viewModel.feature.value = feature }
    }

    // MapChangeListener
    override fun onViewComplete() {
        super.onViewComplete()
        pickFeatureAtPosition(mapController.cameraPosition)
    }
    override fun onRegionIsChanging() {
        // update the map metadata as you move the map
        super.onRegionIsChanging()
        pickFeatureAtPosition(mapController.cameraPosition)
    }
    override fun onRegionDidChange(animated: Boolean) {
        // update the map metadata when done moving, including on initial load
        super.onRegionDidChange(animated)
        pickFeatureAtPosition(mapController.cameraPosition)
    }

    private fun pickFeatureAtPosition(cameraPosition: CameraPosition) {
        val center = mapController.lngLatToScreenPosition(cameraPosition.position)
        mapController.pickFeature(center.x, center.y)
    }
}
