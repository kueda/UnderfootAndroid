package rocks.underfoot.underfootandroid.maptuils

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.mapzen.tangram.MapChangeListener
import com.mapzen.tangram.MapController

/**
 * ViewModel or other class that receives a Tangram MapController the center point and zoom on all
 * map changes.
 */
interface MapCenterTrackable {
//    var mapController: MapController
    val lat: MutableLiveData<Double>
    val lng: MutableLiveData<Double>
    val zoom: MutableLiveData<Float>
    val trackingUserLocation: MutableLiveData<Boolean>

    fun onMapReady(mc: MapController) {
        mc.setMapChangeListener(object: MapChangeListener {
            override fun onViewComplete() {}
            override fun onRegionWillChange(animated: Boolean) {}
            override fun onRegionIsChanging() {
                // update the map metadata as you move the map
                val p = mc.cameraPosition
                zoom.value = p.zoom
                lat.value = p.position.latitude
                lng.value = p.position.longitude
            }
            override fun onRegionDidChange(animated: Boolean) {
                // update the map metadata when done moving, including on initial load
                val p = mc.cameraPosition
                zoom.value = p.zoom
                lat.value = p.position.latitude
                lng.value = p.position.longitude
                val center = mc.lngLatToScreenPosition(p.position)
                mc.pickFeature(center.x, center.y)
            }
        })
    }
}
