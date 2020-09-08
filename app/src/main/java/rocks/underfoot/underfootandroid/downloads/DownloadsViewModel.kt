package rocks.underfoot.underfootandroid.downloads

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

// TODO move these models into their own package as I'm probably going to need them in the maps to
//  determine what's been downloaded
// Decorator from kotlinx.serialization allows this class to go to and from JSON
@Serializable
data class Pack(
    val name: String,
    val path: String
)

@Serializable
data class Manifest(
    val packs: List<Pack>,
    val updated_at: String
)

// A wrapper around the pack which holds some data that doesn't need to be serialized
data class Download(
    val pack: Pack
) {
    var downloaded = false
    var downloading = false

    var maxBytes = 0
    var downloadedBytes = 0
    var downloadID = -1L

    // TODO determine this based on whether the file is downloaded and it's an older version
    val updatable = false
}

// Stores data about the progress of a download
data class DownloadProgress(val bytesDownloaded: Int = 0, val totalBytes: Int = 0)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "DownloadsViewModel"
    private val baseUrl = "https://underfoot.rocks"

    // Simple serializer from kotlinx.serialization. Docs at
    // https://github.com/Kotlin/kotlinx.serialization
    private val json = Json { ignoreUnknownKeys = true }

    // Live data of all the downloads to show in the list
    val downloads = MutableLiveData<List<Download>>(listOf<Download>())

    // Map of pack names to progress. This is where things get lame: when I set things in this map,
    // the LiveData will notify its observers (though annoyingly not any data bindings), so I can
    // detect changes and update the list when I need to, say, update the progress bar. While it
    // might make more sense to just update the downloads livedata directly, setting attributes on
    // items in a list will *not* notify observers, so there would be no way to tell the list
    // adapter that the list needs to update. This way of doing this is so phenomenally ugly that
    // I'm sure there's a better way to do it, but I haven't figured it out yet.
    val progressMap = MutableLiveData<MutableMap<String, DownloadProgress>>(mutableMapOf<String, DownloadProgress>())

    lateinit var downloadManager: DownloadManager

    fun fetchManifest() {
        Log.d(tag, "fetchManifest")

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // URL is stock java. Supposedly not a good way to retrieve a large file, and
                    // clearly doesn't have any bells and whistles, but it fetches stuff at a URL.
                    // I tried Fuel and found it under-documented. Retrofit and Ktor both seemed
                    // like overkill for just fetching a JSON file
                    val jsonString = URL("${baseUrl}/manifest.json").readText()
                    val manifest = json.decodeFromString<Manifest>(jsonString)
                    val newDownloads = manifest.packs.map { Download(pack = it) }
//                    Note, just setting the value here will raise an exception about setting
//                    attributes on the wrong thread
                    downloads.postValue(newDownloads)
                    checkPacksDownloaded()
                } catch (ex: FileNotFoundException) {
                    Log.d(tag, "File not found, basically 404")
                } catch (ex: IOException) {
                    Log.d(tag, "Some other network problem: $ex")
                }
            }
        }
    }

    fun startDownload(download: Download) {
        val url = listOf(baseUrl, download.pack.path).joinToString("/")
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        // TODO this should really use the path attribute of the pack and recursively make any subdirectories
        request.setDestinationInExternalFilesDir(getApplication(), "underfoot", "${download.pack.name}.zip")
        download.downloadID = downloadManager.enqueue(request)
        download.downloading = true
        // There is apparently no other way of explicitly telling LiveData that even if its value
        // hasn't changed, some nested properties have
        downloads.postValue(downloads.value)

        // Query the download manager in a thread until it finishes
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val q = DownloadManager.Query()
                q.setFilterById(download.downloadID)
                while (download.downloading) {
                    val cursor = downloadManager.query(q)
                    cursor.moveToFirst()
                    if (cursor.count <= 0) {
                        download.downloading = false
                        break
                    }
                    when (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            download.downloading = false
                            // TODO So much: move the pack to internal storage, unzip contents,
                            //  delete the zip write pack json to disk with it, change
                            //  checkPacksDownloaded to look for the directory and not this zip
                            //  file, alter the map to look for a downloaded pack
                            checkPacksDownloaded()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            download.downloading = false
                            // Todo make this available in the UI, maybe add it as a property of DownloadProgress
                            val error = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                            Log.d(tag, "download failed: $error")
                        }
                        else -> {
                            val downloadedBytes =
                                cursor.getInt(cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )
                            val totalBytes = cursor.getInt(cursor.getColumnIndex(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                            ) )
                            progressMap.value?.let {
                                it[download.pack.name] = DownloadProgress(
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                            // Again, trick LiveData into notifying observers about this change
                            progressMap.postValue(progressMap.value)
                        }
                    }
                }
            }
        }
    }

    fun deletePack(pack: Pack) {
        val extFiles = getApplication<Application>().getExternalFilesDir("underfoot")
        val packFile = File(extFiles, "${pack.name}.zip")
        if (packFile.exists()) {
            packFile.delete()
            checkPacksDownloaded()
        }
    }

    private fun checkPacksDownloaded() {
        val extFiles = getApplication<Application>().getExternalFilesDir("underfoot")
        for (download in downloads.value ?: listOf()) {
            val packPath = File(extFiles, "${download.pack.name}.zip")
            download.downloaded = packPath.exists()
        }
        // This forces an update on the livedata so things like data bindings update. This
        // feels... not good
        downloads.postValue(downloads.value)
    }

    fun cancelDownload(download: Download) {
        if (download.downloadID <= 0) return
        val numRemoved = downloadManager.remove(download.downloadID)
        download.downloadID = 0
        download.downloadedBytes = 0
        download.maxBytes = 0
        download.downloading = false
        downloads.postValue(downloads.value)
        Log.d(tag, "cancelDownload, removed $numRemoved downloads")
    }

}
