package rocks.underfoot.underfootandroid.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application): AndroidViewModel(application) {
    private val repository = PacksRepository(application)
    val packs: LiveData<List<Pack>> = Transformations.map(repository.packs) { packs ->
        packs.sortedBy { p -> p.name }
    }
    private val selectedPack = repository.selectedPack
    val selectedPackName: LiveData<String> = Transformations.map(selectedPack) { pack -> pack?.name ?: "" }
    val packsChangedAt = repository.packsChangedAt
    val online = repository.online

    init {
        viewModelScope.launch {
            repository.load()
        }
    }


    override fun onCleared() {
        super.onCleared()
        repository.unload()
    }

    fun reload() {
        viewModelScope.launch {
            repository.reload()
        }
    }

    fun selectPack(pack: Pack) {
        repository.selectPack(pack)
    }

    fun downloadPack(pack: Pack) {
        viewModelScope.launch {
            repository.downloadPack(pack)
        }
    }

    fun cancelDownload(pack: Pack) {
        repository.cancelDownload(pack)
    }

    fun deletePack(pack: Pack) {
        viewModelScope.launch {
            repository.deletePack(pack)
        }
    }
}
