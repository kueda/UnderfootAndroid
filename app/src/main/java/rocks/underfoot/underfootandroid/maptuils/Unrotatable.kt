package rocks.underfoot.underfootandroid.maptuils

import com.mapzen.tangram.MapController
import com.mapzen.tangram.TouchInput

interface Unrotatable {
    fun onMapReady(mc: MapController) {
        val touchInput = mc.touchInput ?: return
        touchInput.setShoveResponder(object: TouchInput.ShoveResponder {
            // Disable perspective changes, though dang, how cool would it be to have a 3D DEM
            override fun onShoveBegin(): Boolean { return false }
            override fun onShove(distance: Float): Boolean { return true }
            override fun onShoveEnd(): Boolean { return false }
        })
    }
}
