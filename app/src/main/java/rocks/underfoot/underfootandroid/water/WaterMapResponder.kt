package rocks.underfoot.underfootandroid.water

import android.graphics.Color
import androidx.lifecycle.LifecycleOwner
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
}
