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
    private lateinit var userLocationMarker: Marker

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
                when {
                    viewModel.cameraPosition.value == null -> {
                        if (
                            // If no pack has been selected, we don't want to request GPS permission
                            !viewModel.selectedPackName.value.isNullOrEmpty()
                            && viewModel.initialCameraUpdate.value == null
                        ) {
                            // If it's a brand new view model, start requesting updates so the map goes to
                            // the user's current location
                            viewModel.requestLocationUpdates()
                        } else {
                            viewModel.initialCameraUpdate.value?.let {
                                viewModel.cameraUpdate.value = it
                                viewModel.initialCameraUpdate.value = null
                            }
                        }
                    }
                    else -> {
                        // If there's an existing view model, pan/zoom to wherever it was last
                        Log.d(TAG, "Setting initial camera from existing view model")
                        viewModel.cameraUpdate.value = viewModel.cameraPosition.value?.let { cp ->
                            CameraUpdateFactory.newCameraPosition(cp)
                        }
                    }
                }
            }
        }
        // The scene needs to be customized based on the pack the user has chosen. Since that's a
        // part of state, the view model provides that
        viewModel.sceneUpdatesForSelectedPack.observe(viewLifecycleOwner, { updates ->
            mapController.loadSceneFile(SCENE_FILE_PATH, updates)
        })
        viewModel.cameraUpdate.observe(viewLifecycleOwner, { cameraUpdate ->
            cameraUpdate?.let {
                mapController.updateCameraPosition(it, 500)
                viewModel.cameraUpdate.value = null
            }
        })
        userLocationMarker = mapController.addMarker()
        userLocationMarker.isVisible = false
        // TODO replace this with a more traditional pulsating orb... when yuo figure out how to
        //  do animations
        userLocationMarker.setStylingFromString("""
            {
                style: 'points',
                color: [1, 0.25, 0.5, 0.5],
                size: [10px, 10px],
                order: 2000,
                collide: false
            }
        """.trimIndent());
        viewModel.userLocation.observe(viewLifecycleOwner, { loc ->
            if (loc == null) {
                userLocationMarker.isVisible = false
            } else {
                userLocationMarker.isVisible = true
                userLocationMarker.setPoint(LngLat(loc.longitude, loc.latitude))
            }
        })
    }

    // MapChangeListener
    override fun onViewComplete() {}
    override fun onRegionWillChange(animated: Boolean) {}
    override fun onRegionIsChanging() {
        // update the map metadata as you move the map
        viewModel.cameraPosition.value = mapController.cameraPosition
        pickFeatureAtPosition(mapController.cameraPosition)
    }
    override fun onRegionDidChange(animated: Boolean) {
        // update the map metadata when done moving, including on initial load
        viewModel.cameraPosition.value = mapController.cameraPosition
        pickFeatureAtPosition(mapController.cameraPosition)
    }
    private fun pickFeatureAtPosition(cameraPosition: CameraPosition) {
        val center = mapController.lngLatToScreenPosition(cameraPosition.position)
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
