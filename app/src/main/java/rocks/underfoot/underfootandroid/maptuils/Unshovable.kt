package rocks.underfoot.underfootandroid.maptuils

import com.mapzen.tangram.MapController
import com.mapzen.tangram.TouchInput

interface Unshovable {
    fun onMapReady(mc: MapController) {
        val touchInput = mc.touchInput ?: return
        touchInput.setRotateResponder(object: TouchInput.RotateResponder {
            // Disable rotation
            override fun onRotateBegin(): Boolean { return true }
            override fun onRotate(x: Float, y: Float, rotation: Float): Boolean { return true }
            override fun onRotateEnd(): Boolean { return true }
        });
    }
}