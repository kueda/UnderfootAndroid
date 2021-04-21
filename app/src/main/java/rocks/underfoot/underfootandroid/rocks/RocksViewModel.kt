package rocks.underfoot.underfootandroid.rocks

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.lifecycle.*
import com.mapzen.tangram.*
import rocks.underfoot.underfootandroid.maptuils.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RocksViewModel : MapViewModel() {
    companion object {
        private const val TAG = "RocksViewModel"
    }

    override val sceneUpdatesForSelectedPack: LiveData<List<SceneUpdate>> = Transformations.map(selectedPackName) { packName ->
        listOf(
            SceneUpdate(
                "sources.underfoot.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/rocks.mbtiles"
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
    val rocksMbtilesPath = Transformations.map(selectedPackName) { packName ->
        "/data/user/0/rocks.underfoot.underfootandroid/files/${packName}/rocks.mbtiles"
    }

    var repository: RockUnitsRepository? = null

    override val sceneFilePath = MutableLiveData<String>(RocksMapResponder.SCENE_FILE_PATH)

    val feature = MutableLiveData<FeaturePickResult>()
    private val rockUnit: LiveData<RockUnit> = Transformations.map(feature) { f ->
        if (f == null) {
            RockUnit()
        } else {
            val id = featurePropertyString("id", f.properties)
            repository?.find(id) ?: RockUnit()
        }
    }
    val featureTitle: LiveData<String> = Transformations.map(rockUnit) {
        it?.title
    }
    val featureLithology: LiveData<String> = Transformations.map(feature) { f ->
        if (f == null) {
            ""
        } else {
            featurePropertyString("lithology", f.properties, default = "Lithology: Unknown")
        }
    }
    val featureDescription: LiveData<String> = Transformations.map(rockUnit) { it?.description }
    val featureAge: LiveData<String> = Transformations.map(rockUnit) {
        val estAge = humanizeAge(it.estAge);
        val span = it.span.capitalize().replace(" To ", " to ")
        "Age: $span ($estAge)"
    }
    val featureEstAge: LiveData<String> = Transformations.map(rockUnit) {
        val span = it.span.capitalize().replace(" To ", " to ")
        "$span (${humanizeAge(it.maxAge)} - ${humanizeAge(it.minAge)})"
    }
    val featureSource: LiveData<String> = Transformations.map(rockUnit) {
        it.source
    }
    val featureCitation: LiveData<String> = Transformations.map(rockUnit) {
        it.citation
    }

    private fun humanizeAge(ageArg: String?): String {
        val default = "?"
        val age = if (ageArg.isNullOrEmpty()) return default else ageArg
        val ageNum = age.toFloatOrNull() ?: return default
        return when {
            ageNum >= 1000000000 -> "%.1f Ga".format(ageNum / 1000000000.0)
            ageNum >= 1000000 -> "%.1f Ma".format(ageNum / 1000000.0)
            ageNum >= 100000 -> "%.1f ka".format(ageNum / 1000.0)
            else -> "%,d years".format(ageNum.roundToInt())
        }
    }

    private fun featurePropertyString(
        propName: String,
        properties: Map<String, String>,
        default: String = "Unknown"
    ): String {
        if (properties.isEmpty()) return default
        properties[propName]?.let {
            if (it.isNotEmpty()) return it
        }
        return default
    }
}
