package rocks.underfoot.underfootandroid.downloads

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.databinding.DataBindingUtil
import rocks.underfoot.underfootandroid.databinding.DownloadListItemBinding
import java.lang.IllegalStateException

// Derived from https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksAdapter.kt
//  and https://github.com/trsquarelab/androidexamples/blob/master/DataBindingListView/src/main/java/com/example/databindinglistview/ListAdapter.java
class PacksAdapter(
    private var packs: List<Pack> = listOf<Pack>(),
    private var viewModel: DownloadsViewModel
) : BaseAdapter() {
    override fun getCount() = packs.size

    override fun getItem(position: Int) = packs[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: DownloadListItemBinding
        binding = if (convertView == null) {
            // If we have to make a new view, get an inflater from the parent and make a new view
            // with pack_list_item.xml using its generated binding class
            val inflater = LayoutInflater.from(parent.context)
            DownloadListItemBinding.inflate(inflater, parent, false)
        } else {
            // If there *is* a view that we're recycling, get its binding
            DataBindingUtil.getBinding(convertView) ?: throw IllegalStateException()
        }
        binding.pack = getItem(position)
        binding.viewModel = viewModel
//        This is in the architecture sample app but doesn't seem necessary here
        binding.executePendingBindings()
        return binding.root
    }

    fun updateDownloads(newPacks: List<Pack>) {
        Log.d("DownloadsAdapter", "adapting ${newPacks.size} new packs")
        packs = newPacks
//        This *is* necessary
        notifyDataSetChanged()
    }
}
