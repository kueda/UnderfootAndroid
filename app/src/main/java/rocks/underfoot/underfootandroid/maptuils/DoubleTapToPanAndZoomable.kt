package rocks.underfoot.underfootandroid.maptuils

import android.graphics.PointF
import com.mapzen.tangram.CameraUpdateFactory
import com.mapzen.tangram.LngLat
import com.mapzen.tangram.MapController

interface DoubleTapToPanAndZoomable {
    fun panToLocation(lngLat: LngLat, zoom: Float?, manual: Boolean)

    fun onMapReady(mc: MapController) {
        val touchInput = mc.touchInput ?: return
        touchInput.setDoubleTapResponder { x, y ->
            // Zoom on double tap
            val newZoom = mc.cameraPosition.zoom + 1F
            val tapped = mc.screenPositionToLngLat(PointF(x, y))
            tapped?.let {
                panToLocation(it, newZoom, manual = true)
            }
            true
        }
    }
}
