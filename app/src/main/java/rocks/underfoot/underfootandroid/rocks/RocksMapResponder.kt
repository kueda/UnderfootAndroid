package rocks.underfoot.underfootandroid.rocks

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.LifecycleOwner
import com.mapzen.tangram.*

// Responds to map events to update the view model, and observes the view model to control the map.
// Might be appropriate to call it a MapPresenter?
class RocksMapResponder(
    private val viewModel: RocksViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private var colorAccent: Int = Color.CYAN
) : MapView.MapReadyCallback,
    MapChangeListener,
    MapController.SceneLoadListener,
    TouchInput.TapResponder,
    TouchInput.DoubleTapResponder {
    companion object {
        private const val TAG = "RocksMapResponder"
        const val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
        const val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
        const val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"
    }
    private lateinit var mapController: MapController
    private var userLocationMarker: Marker? = null
    private var userLocationAccMarker: Marker? = null

    fun onDestroyView() {
        mapController.removeAllMarkers()
        userLocationMarker = null
        userLocationAccMarker = null
    }

    override fun onMapReady(mc: MapController?) {
        mapController = mc!!
        mapController.setMapChangeListener(this)
        mapController.setFeaturePickListener { feature -> viewModel.feature.value = feature }
        mapController.touchInput?.let {ti ->
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
        mapController.setSceneLoadListener(this)
        // The scene needs to be customized based on the pack the user has chosen. Since that's a
        // part of state, the view model provides that
        viewModel.sceneUpdatesForSelectedPack.observe(viewLifecycleOwner, { updates ->
            mapController.loadSceneFile(viewModel.sceneFilePath.value, updates)
        })
        viewModel.cameraUpdate.observe(viewLifecycleOwner, { cameraUpdate ->
            cameraUpdate?.let {
                mapController.updateCameraPosition(it)
                viewModel.cameraUpdate.value = null
            }
        })
        viewModel.animatedCameraUpdate.observe(viewLifecycleOwner, { cameraUpdate ->
            cameraUpdate?.let {
                mapController.updateCameraPosition(it, 500)
                viewModel.cameraUpdate.value = null
            }
        })
        viewModel.sceneFilePath.observe(viewLifecycleOwner, {
            mapController.loadSceneFile(
                viewModel.sceneFilePath.value,
                viewModel.sceneUpdatesForSelectedPack.value
            )
        })
    }

    override fun onSceneReady(sceneId: Int, sceneError: SceneError?) {
        if (sceneError != null) {
            Log.d(TAG, "Scene update errors ${sceneError.sceneUpdate} ${sceneError.error}")
            return
        }
        // Kind of dumb, but the user location observer below attempts to make these visible.
        // Changing the scene will remove them from the map anyway, but if the local references to
        // them persist, the observer will refer to an object that doesn't really exist, so here
        // I'm explicitly removing them.
        mapController.removeAllMarkers()
        userLocationAccMarker = null
        userLocationMarker = null
        if (viewModel.cameraPosition.value == null) {
            if (
                // If no pack has been selected, we don't want to request GPS permission
                !viewModel.selectedPackName.value.isNullOrEmpty()
                && viewModel.initialCameraUpdate.value == null
            ) {
                // If it's a brand new view model, start requesting updates so the map goes to
                // the user's current location
//                Log.d(TAG, "initial camera from current location")
                viewModel.panToCurrentLocation()
            } else {
//                Log.d(TAG, "initial camera from initialCameraUpdate")
                viewModel.initialCameraUpdate.value?.let {
                    viewModel.cameraUpdate.value = it
                    viewModel.initialCameraUpdate.value = null
                }
            }
        } else {
            // If there's an existing view model, pan/zoom to wherever it was last
            viewModel.cameraPosition.value?.let { cp ->
                viewModel.panToLocation(cp.position, cp.zoom)
            }
        }
        // Set up the current location marker. This is in onSceneReady b/c it will error if there's
        // no scene
        if (userLocationAccMarker == null) {
            userLocationAccMarker = mapController.addMarker()
            userLocationAccMarker?.apply {
                isVisible = false
                val initialMarkerSize = "100.0 / \$meters_per_pixel"
                setStylingFromString("""
                    {
                        style: 'points',
                        color: green,
                        size: 'function() { return ($initialMarkerSize) + "px"; }',
                        order: 1800,
                        collide: false
                    }
                """.trimIndent())
            }
        }
        if (userLocationMarker == null) {
            userLocationMarker = mapController.addMarker()
            userLocationMarker?.apply {
                isVisible = false
                setStylingFromString("""
                    {
                        style: 'points',
                        color: 'rgb(${colorAccent.red}, ${colorAccent.green}, ${colorAccent.blue})',
                        size: [10px, 10px],
                        order: 2000,
                        collide: false,
                        outline: { color: white, width: 2px }
                    }
                """.trimIndent())
            }
        }
        viewModel.userLocation.observe(viewLifecycleOwner, { loc ->
            if (loc == null) {
                userLocationMarker?.isVisible = false
                userLocationAccMarker?.isVisible = false
                return@observe
            }
            userLocationMarker?.apply {
                isVisible = true
                setPoint(LngLat(loc.longitude, loc.latitude))
            }
            userLocationAccMarker?.apply {
                isVisible = true
                setPoint(LngLat(loc.longitude, loc.latitude))
                val newMarkerSize = "${loc.accuracy} / \$meters_per_pixel"
                setStylingFromString("""
                    {
                        style: 'points',
                        color: 'rgba(${colorAccent.red}, ${colorAccent.green}, ${colorAccent.blue}, 0.5)',
                        size: 'function() { return ($newMarkerSize) + "px"; }',
                        order: 1800,
                        collide: false
                    }
                """.trimIndent())
            }
        })
    }

    // MapChangeListener
    override fun onViewComplete() {
        viewModel.cameraPosition.value = mapController.cameraPosition
        pickFeatureAtPosition(mapController.cameraPosition)
    }
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
        Log.d(TAG, "onDoubleTap, tapped: $tapped")
        tapped?.let {
            viewModel.panToLocation(it, newZoom, manual = true)
        }
        return false
    }
}
