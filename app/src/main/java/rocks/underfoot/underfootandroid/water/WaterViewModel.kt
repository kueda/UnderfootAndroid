package rocks.underfoot.underfootandroid.water

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.mapzen.tangram.SceneUpdate
import rocks.underfoot.underfootandroid.maptuils.MapViewModel

class WaterViewModel : MapViewModel() {
    override val sceneUpdatesForSelectedPack: LiveData<List<SceneUpdate>> = Transformations.map(selectedPackName) { packName ->
        listOf(
            SceneUpdate(
                "sources.water.url",
                "file:///data/user/0/rocks.underfoot.underfootandroid/files/${packName}/water.mbtiles"
            )
        )
    }
    override val sceneFilePath = MutableLiveData<String>(WaterMapResponder.SCENE_FILE_PATH)
}
