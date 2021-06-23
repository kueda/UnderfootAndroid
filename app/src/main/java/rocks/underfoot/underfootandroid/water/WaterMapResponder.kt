package rocks.underfoot.underfootandroid.water

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.mapzen.tangram.CameraPosition
import com.mapzen.tangram.MapController
import rocks.underfoot.underfootandroid.maptuils.MapResponder

class WaterMapResponder(
    private val viewModel: WaterViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private var colorAccent: Int = Color.CYAN
) : MapResponder(viewModel, viewLifecycleOwner, colorAccent) {
    companion object {
        private const val TAG = "WatersMapResponder"
        const val SCENE_FILE_PATH = "asset:///water.yml"
    }

    override fun onMapReady(mc: MapController?) {
        super.onMapReady(mc)
        mapController.setPickRadius(10.0F)
        mapController.setFeaturePickListener { feature ->
            viewModel.feature.value = feature
            // TODO when the viewmodel has a feature, add a FAB that highlights downstream segments
            // and zooms to their bounding box
            Log.d(TAG, "feature: ${feature?.properties}")
            if (feature == null) {
                viewModel.watershedName.value = null
            } else {
                if (!feature.properties.contains("type")) {
                    viewModel.watershedName.value = feature.properties["name"]
                }
            }
        }
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
