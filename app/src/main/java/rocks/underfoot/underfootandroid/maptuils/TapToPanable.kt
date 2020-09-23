package rocks.underfoot.underfootandroid.maptuils

import android.graphics.PointF
import com.mapzen.tangram.CameraUpdateFactory
import com.mapzen.tangram.LngLat
import com.mapzen.tangram.MapController
import com.mapzen.tangram.TouchInput

interface TapToPanable {
    fun panToLocation(lngLat: LngLat, zoom: Float?, manual: Boolean)

    fun onMapReady(mc: MapController) {
        val touchInput = mc.touchInput ?: return
        touchInput.setTapResponder(object: TouchInput.TapResponder {
            override fun onSingleTapUp(x: Float, y: Float): Boolean {
                return false
            }

            override fun onSingleTapConfirmed(x: Float, y: Float): Boolean {
                mc.screenPositionToLngLat(PointF(x, y))?.let { tapped ->
                    panToLocation(tapped, mc.cameraPosition.zoom, true)
                }
                return true
            }

        });
    }
}
