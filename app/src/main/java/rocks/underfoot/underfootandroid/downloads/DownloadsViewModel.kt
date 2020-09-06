package rocks.underfoot.underfootandroid.downloads

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.FileNotFoundException
import java.io.IOException

// TODO move these models into their own package as I'm probably going to need them in the maps to
//  determine what's been downloaded
// Decorator from kotlinx.serialization allows this class to go to and from JSON
@Serializable
data class Pack(val name: String) {}

@Serializable
data class Manifest(
    val packs: List<Pack>,
    val updated_at: String
) {
    val updatedAt: LocalDate
        get() = LocalDate.parse(updated_at, DateTimeFormatter.ISO_DATE_TIME)
}

class DownloadsViewModel : ViewModel() {
    val tag = "DownloadsViewModel"

    // Simple serializer from kotlinx.serialization. Docs at
    // https://github.com/Kotlin/kotlinx.serialization
    private val json = Json { ignoreUnknownKeys = true }

    val packs = MutableLiveData<List<Pack>>(listOf<Pack>())

    fun fetchManifest() {
        Log.d(tag, "fetchManifest")

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // URL is stock java. Supposedly not a good way to retrieve a large file, and
                    // clearly doesn't have any bells and whistles, but it fetches stuff at a URL.
                    // I tried Fuel and found it under-documented. Retrofit and Ktor both seemed
                    // like overkill for just fetching a JSON file
                    val jsonString = URL("https://underfoot.rocks/manifest.json").readText()

                    val manifest = json.decodeFromString<Manifest>(jsonString)
//                    Note, just setting the value here will raise an exception about setting
//                    attributes on the wrong thread
//                    packs.value = manifest.packs
                    packs.postValue(manifest.packs)
                } catch (ex: FileNotFoundException) {
                    Log.d(tag, "File not found, basically 404")
                } catch (ex: IOException) {
                    Log.d(tag, "Some other network problem: $ex")
                }
            }
        }
    }
}
