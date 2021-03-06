package rocks.underfoot.underfootandroid.water

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.mapzen.tangram.FeaturePickResult
import com.mapzen.tangram.SceneUpdate
import rocks.underfoot.underfootandroid.maptuils.MapViewModel

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
    val featureIsWaterway = Transformations.map(feature) {
        it?.let {
            // Brittle, but there's no way to detect the source of the feature except via properties
            it.properties["type"] in arrayOf(
                "stream",
                "siphon",
                "aqueduct",
                "canal/ditch",
                "artificial",
                "connector"
            )
        } ?: false
    }
    var repository: WaterRepository? = null
    val mbtilesPath = Transformations.map(selectedPackName) { packName ->
        "/data/user/0/rocks.underfoot.underfootandroid/files/${packName}/water.mbtiles"
    }
    val highlightSegments = MutableLiveData<List<String>>()

    val featureTitle: LiveData<String> = Transformations.map(feature) {
        it?.let {
            val name = it.properties["name"] ?: "Unknown"
            val type = it.properties["type"] ?: "Watershed"
            "$name ($type)"
        } ?: "Unknown"
    }
    val watershedFeature = MutableLiveData<FeaturePickResult>()
    val watershedName = Transformations.map(watershedFeature) {
        it?.let {
            it.properties["name"]
        } ?: "Unknown"
    }
    val featureCitation: LiveData<String> = Transformations.map(feature) { f ->
        repository?.let {
            val source = f?.let { f.properties["source"] }
            if (!source.isNullOrBlank()) {
                it.citationForSource(source)
            } else null
        } ?: "Unknown"
    }
    val watershedCitation: LiveData<String> = Transformations.map(watershedFeature) { f ->
        repository?.let {
            val source = f?.let { f.properties["source"] }
            if (!source.isNullOrBlank()) {
                it.citationForSource(source)
            } else null
        } ?: "Unknown"
    }

    fun showDownStream() {
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
