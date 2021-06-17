package rocks.underfoot.underfootandroid.water

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.mapzen.tangram.FeaturePickResult
import com.mapzen.tangram.SceneUpdate
import rocks.underfoot.underfootandroid.maptuils.MapViewModel
import rocks.underfoot.underfootandroid.rocks.WaterRepository

class WaterViewModel : MapViewModel() {
    companion object {
        private const val TAG = "WaterViewModel"
    }
    override val sceneUpdatesForSelectedPack: LiveData<List<SceneUpdate>> = Transformations.map(selectedPackName) { packName ->
        listOf(
            SceneUpdate(
                "sources.water.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/water.mbtiles"
            ),
            SceneUpdate(
                "sources.ways.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/ways.mbtiles"
            ),
            SceneUpdate(
                "sources.elevation.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/contours.mbtiles"
            )
        )
    }
    override val sceneFilePath = MutableLiveData<String>(WaterMapResponder.SCENE_FILE_PATH)
    val feature = MutableLiveData<FeaturePickResult>()
    var repository: WaterRepository? = null
    val mbtilesPath = Transformations.map(selectedPackName) { packName ->
        "/data/user/0/rocks.underfoot.underfootandroid/files/${packName}/water.mbtiles"
    }
    val highlightSegments = MutableLiveData<List<String>>()

    fun showDownStream() {
        Log.d(TAG, "tapped downstream")
        // TODO fetch segments to highlight from WaterRepository
        if (repository !== null && feature.value !== null) {
            feature.value?.let {f ->
                repository?.let {r ->
                    val downstreamIds = r.downstream(f.properties["source_id"])
                    Log.d(TAG, "downstreamIds: $downstreamIds")
                    highlightSegments.value = downstreamIds
                }
            }
        }
    }
}
