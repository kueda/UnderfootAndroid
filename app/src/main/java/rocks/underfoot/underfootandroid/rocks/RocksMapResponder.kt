package rocks.underfoot.underfootandroid.rocks

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.mapzen.tangram.*

// Responds to map events to update the view model, and observes the view model to control the map.
// Might be appropriate to call it a MapPresenter?
class RocksMapResponder(
    private val viewModel: RocksViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : MapView.MapReadyCallback, MapChangeListener, TouchInput.TapResponder, TouchInput.DoubleTapResponder {
    companion object {
        private const val TAG = "RocksMapResponder"
        private const val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
        private const val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
        private const val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"
    }
    private lateinit var mapController: MapController

    init {
        viewModel.cameraUpdate.observe(viewLifecycleOwner, Observer {cameraUpdate ->
            cameraUpdate?.let {
                mapController.updateCameraPosition(cameraUpdate, 500)
                viewModel.cameraUpdate.value = null
            }
        })
    }

    override fun onMapReady(mc: MapController?) {
        mapController = mc!!
        mapController.setMapChangeListener(this)
        mapController.setFeaturePickListener { feature ->
            feature?.let { viewModel.feature.value = feature }
        }
        mc.touchInput?.let {ti ->
            ti.setTapResponder(this);
            ti.setDoubleTapResponder(this)
            // Disable perspective changes, though dang, how cool would it be to have a 3D DEM
            ti.setShoveResponder(object: TouchInput.ShoveResponder {
                override fun onShoveBegin() = false
                override fun onShove(distance: Float) = true
                override fun onShoveEnd() = false
            })
            // Disable rotation
            ti.setRotateResponder(object: TouchInput.RotateResponder {
                override fun onRotateBegin() = true
                override fun onRotate(x: Float, y: Float, rotation: Float) = true
                override fun onRotateEnd() = true
            })
            // Set the pan responder to an object that delegates most of its functionality to the
            // default PanResponder, but stops tracking user location onPan
            val defaultPanResponder = mapController.panResponder
            ti.setPanResponder(object : TouchInput.PanResponder by defaultPanResponder {
                override fun onPanEnd(): Boolean {
                    // custom thing
                    viewModel.trackingUserLocation.value = false
                    return defaultPanResponder.onPanEnd()
                }
            })
        }
        mapController.setSceneLoadListener { _, sceneError ->
            if (sceneError != null) {
                Log.d(TAG, "Scene update errors ${sceneError.sceneUpdate} ${sceneError.error}")
            } else {
                if (viewModel.cameraPosition.value == null ) {
                    // If it's a brand new view model, start requesting updates so the map goes to
                    // the user's current location
                    // TODO make this zoom to the extent of the current pack and *show* the user's
                    //  current location
                    viewModel.requestLocationUpdates()
                } else {
                    // If there's an existing view model, pan/zoom to wherever it was last
                    viewModel.cameraUpdate.value = viewModel.cameraPosition.value?.let { cp ->
                        CameraUpdateFactory.newCameraPosition(cp)
                    }
                }
            }
        }
        // The scene needs to be customized based on the pack the user has chosen. Since that's a
        // part of state, the view model provides that
        viewModel.sceneUpdatesForSelectedPack.observe(viewLifecycleOwner, Observer {updates ->
            mapController.loadSceneFile(SCENE_FILE_PATH, updates)
        })
    }

    // MapChangeListener
    override fun onViewComplete() {}
    override fun onRegionWillChange(animated: Boolean) {}
    override fun onRegionIsChanging() {
        // update the map metadata as you move the map
        viewModel.cameraPosition.value = mapController.cameraPosition
    }
    override fun onRegionDidChange(animated: Boolean) {
        // update the map metadata when done moving, including on initial load
        val p = mapController.cameraPosition
        viewModel.cameraPosition.value = p
        val center = mapController.lngLatToScreenPosition(p.position)
        mapController.pickFeature(center.x, center.y)
    }

    // TapResponder
    override fun onSingleTapUp(x: Float, y: Float) = false
    override fun onSingleTapConfirmed(x: Float, y: Float): Boolean {
        mapController.screenPositionToLngLat(PointF(x, y))?.let { tapped ->
            viewModel.panToLocation(tapped, mapController.cameraPosition.zoom, true)
        }
        return true
    }

    // DoubleTapResponder
    override fun onDoubleTap(x: Float, y: Float): Boolean {
        // Zoom on double tap
        val newZoom = mapController.cameraPosition.zoom + 1F
        val tapped = mapController.screenPositionToLngLat(PointF(x, y))
        tapped?.let {
            viewModel.panToLocation(it, newZoom, manual = true)
        }
        return false
    }
}
