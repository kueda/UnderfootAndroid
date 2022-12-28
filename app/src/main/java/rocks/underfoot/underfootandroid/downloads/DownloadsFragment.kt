package rocks.underfoot.underfootandroid.downloads

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.*
import rocks.underfoot.underfootandroid.MainActivity
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentDownloadsBinding

class DownloadsFragment : Fragment(), LifecycleObserver {
    companion object {
        const val TAG = "DownloadsFragment"
    }

    private lateinit var viewModel: DownloadsViewModel
    private lateinit var binding: FragmentDownloadsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DownloadsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_downloads,
            container,
            false
        )
        binding.viewModel = viewModel
        // Without this, the binding will not update when the view model updates
        binding.lifecycleOwner = viewLifecycleOwner
        // When packs change (i.e. the packs themselves change, not the list of packs), we need to
        // update the list
        viewModel.packsChangedAt.observe(viewLifecycleOwner, Observer {
            (binding.downloadsList.adapter as PacksAdapter).notifyDataSetChanged()
        })
        // When the selected pack changes, we also need to refresh the list
        viewModel.selectedPackId.observe(viewLifecycleOwner, Observer {
            (binding.downloadsList.adapter as PacksAdapter).notifyDataSetChanged()
        })
        viewModel.packs.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "packs changed")
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // This is roughly how they make an adapter that references the view model in
        // https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksFragment.kt
        binding.downloadsList.adapter = PacksAdapter(viewModel = viewModel)
        activity?.lifecycle?.addObserver(this)
    }

    // https://stackoverflow.com/a/62076948
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreated() {
        activity?.lifecycle?.removeObserver(this)
        view?.findViewById<Toolbar>(R.id.downloads_toolbar)?.let{ toolbar ->
            // Use a custom menu icon with a crude drop shadow. Not ideal.
            (activity as MainActivity).setToolbar(toolbar)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.packs.value?.let {
            if (it.isEmpty()) viewModel.reload()
        }
    }
}
