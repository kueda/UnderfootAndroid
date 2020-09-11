package rocks.underfoot.underfootandroid.downloads

import android.app.Application
import android.app.DownloadManager
import android.content.Context
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
import net.lingala.zip4j.ZipFile
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

    fun path(context: Context): File {
        return File(context.filesDir, pack.name)
    }
}

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "DownloadsViewModel"
    private val baseUrl = "https://underfoot.rocks"

    // Simple serializer from kotlinx.serialization. Docs at
    // https://github.com/Kotlin/kotlinx.serialization
    private val json = Json { ignoreUnknownKeys = true }

    // Live data of all the downloads to show in the list
    val downloads = MutableLiveData<List<Download>>()

    val selectedPackName = MutableLiveData<String>()

    val listNeedsUpdate = MutableLiveData<Boolean>(false)

    lateinit var downloadManager: DownloadManager

    fun fetchManifest() {
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
                    listNeedsUpdate.postValue(true)
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
        val fname = "${download.pack.name}.zip"
        val dirname = "underfoot"
        // TODO this should really use the path attribute of the pack and recursively make any subdirectories
        request.setDestinationInExternalFilesDir(getApplication(), dirname, fname)
        download.downloadID = downloadManager.enqueue(request)
        download.downloading = true
        listNeedsUpdate.postValue(true)

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
                            // unzip contents and move to internal storage
                            val app = getApplication<Application>()
                            val externalDir = app.getExternalFilesDir(dirname)!!
                            val extPath = File(externalDir, fname)
                            val internalDir = app.filesDir
                            ZipFile(extPath).extractAll(internalDir.path)
                            // Delete downloaded zip
                            extPath.delete()
                            // Write pack JSON to internal storage
                            val packDir = File(internalDir, download.pack.name)
                            if (packDir.exists()) {
                                val packJsonFile = File(packDir, "${download.pack.name}.json")
                                packJsonFile.writeText(json.encodeToJsonElement(download.pack).toString())
                            }
//                            internalDir.walk().forEach {
//                                Log.d(tag, "local file: $it")
//                            }
                            if (selectedPackName.value.isNullOrEmpty()) {
                                selectedPackName.postValue(download.pack.name)
                            }
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
                            download.maxBytes = totalBytes
                            download.downloadedBytes = downloadedBytes
                            listNeedsUpdate.postValue(true)
                        }
                    }
                }
            }
        }
    }

    fun deletePack(pack: Pack) {
        val app = getApplication<Application>()
        val dir = app.filesDir
        val packDir = File(dir, pack.name)
        // Delete the files
        if (packDir.exists()) {
            packDir.deleteRecursively()
            checkPacksDownloaded()
        }
        // Reset the livedata
        selectedPackName.value = ""
    }

    fun checkPacksDownloaded() {
        if (downloads.value.isNullOrEmpty()) return
        val dir = getApplication<Application>().filesDir
        for (download in downloads.value ?: listOf()) {
            val packPath = File(dir, download.pack.name)
            download.downloaded = packPath.exists()
        }
        listNeedsUpdate.postValue(true)
    }

    fun cancelDownload(download: Download) {
        if (download.downloadID <= 0) return
        val numRemoved = downloadManager.remove(download.downloadID)
        download.downloadID = 0
        download.downloadedBytes = 0
        download.maxBytes = 0
        download.downloading = false
        listNeedsUpdate.value = true
    }

    fun selectDownload(download: Download) {
        if (!download.downloaded) return
        selectedPackName.value = download.pack.name
        listNeedsUpdate.value = true
    }

}
