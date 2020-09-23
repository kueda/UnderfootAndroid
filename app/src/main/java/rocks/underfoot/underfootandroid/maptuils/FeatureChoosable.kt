package rocks.underfoot.underfootandroid.maptuils

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.mapzen.tangram.FeaturePickResult
import com.mapzen.tangram.MapController

/**
 * A ViewModel (or other class) that receives a Tangram MapController and stores a single picked
 * feature in a MutableLiveData property.
 */
interface FeatureChoosable {
    val feature: MutableLiveData<FeaturePickResult>

    fun onMapReady(mc: MapController) {
        mc.setFeaturePickListener {result ->
            Log.d("FeatureChooser", "onFeaturePickComplete, result: $result")
            result?.let { feature.value = result }
        }
    }
}
