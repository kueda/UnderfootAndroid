package rocks.underfoot.underfootandroid.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.lingala.zip4j.ZipFile
import rocks.underfoot.underfootandroid.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime

data class Pack(val metadata: PackMetadata) {
    val name: String
        get() = metadata.name

    var downloaded = false
    var downloading = false
    var maxBytes = 0
    var downloadedBytes = 0
    var downloadID = -1L
    var updatable = false
}

@Serializable
data class PackMetadata(
    val name: String,
    val description: String? = null,
    val path: String
)

@Serializable
data class Manifest(
    val packs: List<PackMetadata>,
    val updated_at: String
)

class PacksRepository(private val context: Context) {
    private val logTag = "PacksRepository"
    val packs = MutableLiveData<List<Pack>>()
    private val _selectedPack = MutableLiveData<Pack>()
    val selectedPack: LiveData<Pack>
        get() = _selectedPack
    private val _packsChangedAt = MutableLiveData<LocalDateTime>(LocalDateTime.now())
    val packsChangedAt: LiveData<LocalDateTime>
        get() = _packsChangedAt

    private val _online = MutableLiveData<Boolean>(false)
    val online: LiveData<Boolean>
        get() = _online


    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://underfoot.rocks"
    private val downloadManager = (
            context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager)
    private val prefsName = context.getString(R.string.packsPrefName)
    private val selectedPrefName = context.getString(R.string.selectedPackPrefName)

    private val packsObserver = Observer<List<Pack>> {
        fetchSelectedPack()
    }

    suspend fun load() {
        // Don't love this, but I guess it's ok if I remove it in unload()
        packs.observeForever(packsObserver)
        reload()
    }

    suspend fun reload() {
        fetchLocalPacks()
        if (isNetworkAvailable()) fetchRemotePacks()
    }

    fun unload() {
        packs.removeObserver(packsObserver)
    }

    // https://stackoverflow.com/a/53532456
    private fun isNetworkAvailable(): Boolean {
        val cm = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        var networkAvailable = false
        cm.activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)?.let { capabilities ->
                networkAvailable = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
            }
        }
        _online.postValue(networkAvailable)
//        Log.d(logTag, "networkAvailable: $networkAvailable")
        return networkAvailable
    }

    private suspend fun fetchLocalPacks() {
        withContext(Dispatchers.IO) {
            context.filesDir.listFiles(FilenameFilter { _, name ->
                name.endsWith(".json")
            })?.let {jsonFiles ->
                packs.postValue(jsonFiles.map {f ->
                    val packMetadata = json.decodeFromString<PackMetadata>(f.readText())
                    val pack = Pack(packMetadata)
                    pack.downloaded = true
                    pack
                })
            }
        }
    }

    private fun fetchSelectedPack() {
        context.apply { with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
            val selectedPackName = getString(selectedPrefName, "")
            val foundPack = packs.value?.find { p -> p.name == selectedPackName }
            foundPack?.let {
                selectPack(it)
            }
        } }
    }

    private suspend fun fetchRemotePacks() {
        withContext(Dispatchers.IO) {
            try {
                // URL is stock java. Supposedly not a good way to retrieve a large file, and
                // clearly doesn't have any bells and whistles, but it fetches stuff at a URL.
                // I tried Fuel and found it under-documented. Retrofit and Ktor both seemed
                // like overkill for just fetching a JSON file
                val jsonString = URL("${baseUrl}/manifest.json").readText()
                val manifest = json.decodeFromString<Manifest>(jsonString)
                val newPacks = manifest.packs.map {newPackMetadata ->
                    val existing = packs.value?.find { p -> p.name == newPackMetadata.name }
                    Log.d(logTag, "existing: $existing")
                    existing ?: Pack(metadata = newPackMetadata)
                }
                packs.postValue(newPacks)
            } catch (ex: FileNotFoundException) {
                Log.d(logTag, "File not found, basically 404")
            } catch (ex: IOException) {
                Log.d(logTag, "Some other network problem: $ex")
            }
        }
    }

    suspend fun downloadPack(pack: Pack) {
        if (online == null || online.value == false) return
        withContext(Dispatchers.IO) {
            val url = listOf(baseUrl, pack.metadata.path).joinToString("/")
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
            val fname = "${pack.name}.zip"
            val dirname = "underfoot"
            // TODO this should really use the path attribute of the pack and recursively make any subdirectories
            request.setDestinationInExternalFilesDir(context, dirname, fname)
            pack.downloadID = downloadManager.enqueue(request)
            pack.downloading = true
            _packsChangedAt.postValue(LocalDateTime.now())
            val q = DownloadManager.Query()
            q.setFilterById(pack.downloadID)
            while (pack.downloading) {
                val cursor = downloadManager.query(q)
                cursor.moveToFirst()
                if (cursor.count <= 0) {
                    pack.downloading = false
                    break
                }
                when (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        pack.downloading = false
                        // unzip contents and move to internal storage
                        val externalDir = context.getExternalFilesDir(dirname)!!
                        val extPath = File(externalDir, fname)
                        val internalDir = context.filesDir
                        ZipFile(extPath).extractAll(internalDir.path)
                        // Delete downloaded zip
                        extPath.delete()
                        // Write pack JSON to internal storage
                        val packJsonFile = File(internalDir, "${pack.name}.json")
                        packJsonFile.writeText(
                            json.encodeToJsonElement(pack.metadata).toString()
                        )
//                        internalDir.walk().forEach {
//                            Log.d(logTag, "local file: $it")
//                        }
                        if (selectedPack.value == null) {
                            pack.downloaded = true
                            selectPack(pack)
                        }
                        checkPacksDownloaded()
                    }
                    DownloadManager.STATUS_FAILED -> {
                        pack.downloading = false
                        val error =
                            cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                        Log.d(logTag, "download failed: $error")
                    }
                    else -> {
                        val downloadedBytes =
                            cursor.getInt(
                                cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )
                        val totalBytes = cursor.getInt(
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                            )
                        )
                        var changed = false
                        if (pack.maxBytes != totalBytes) {
                            pack.maxBytes = totalBytes
                            changed = true
                        }
                        if (pack.downloadedBytes != downloadedBytes) {
                            pack.downloadedBytes = downloadedBytes
                            changed = true
                        }
                        if (changed) _packsChangedAt.postValue(LocalDateTime.now())
                    }
                }
            }
        }
    }

    private fun checkPacksDownloaded() {
        if (packs.value.isNullOrEmpty()) return
        val dir = context.filesDir
        var changed = false
        for (pack in packs.value ?: listOf()) {
            val packPath = File(dir, pack.name)
            val downloadedWas = pack.downloaded
            pack.downloaded = packPath.exists()
            changed = downloadedWas == pack.downloaded
        }
        if (changed) {
            _packsChangedAt.postValue(LocalDateTime.now())
        }
    }

    fun cancelDownload(pack: Pack) {
        if (pack.downloadID <= 0) return
        val numRemoved = downloadManager.remove(pack.downloadID)
        pack.downloadID = 0
        pack.downloadedBytes = 0
        pack.maxBytes = 0
        pack.downloading = false
        _packsChangedAt.postValue(LocalDateTime.now())
    }

    fun selectPack(pack: Pack?) {
        pack?.let {
            if (!pack.downloaded) return
            if (_selectedPack.value?.name == pack.name) {
                return
            }
        }
        _selectedPack.postValue(pack)
        context.apply { with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
            edit { putString(selectedPrefName, pack?.name) }
        } }
    }

    suspend fun deletePack(pack: Pack) {
        withContext(Dispatchers.IO) {
            val dir = context.filesDir
            val packDir = File(dir, pack.name)
            var deleted = false
            // Delete the dir
            if (packDir.exists()) {
                packDir.deleteRecursively()
                deleted = true
            }
            // Delete the JSON file
            val packJsonFile = File(dir, "${pack.name}.json")
            if (packJsonFile.exists()) {
                packJsonFile.delete()
                deleted = true
            }
            if (deleted) {
                pack.downloaded = false
                if (isNetworkAvailable()) {
                    _packsChangedAt.postValue(LocalDateTime.now())
                    checkPacksDownloaded()
                } else {
                    reload()
                }
            }
            // If we just deleted the selected pack, choose the first downloaded one instead, or set
            // to null
            if (selectedPack.value == pack) {
                val newSelectedPack = packs.value?.firstOrNull {it.downloaded}
                selectPack(newSelectedPack)
            }
        }
    }
}
