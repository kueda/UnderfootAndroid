package rocks.underfoot.underfootandroid.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import rocks.underfoot.underfootandroid.MainActivity
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentDownloadsBinding

//private const val ARG_PARAM1 = "param1"
//private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [DownloadsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class DownloadsFragment : Fragment() {
//    private var param1: String? = null
//    private var param2: String? = null

    val logTag = "DownloadsFragment"

    private lateinit var viewModel: DownloadsViewModel
    private lateinit var binding: FragmentDownloadsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        arguments?.let {
//            param1 = it.getString(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
//        }
        viewModel = ViewModelProvider(this).get(DownloadsViewModel::class.java)
        viewModel.downloadManager = (activity?.getSystemService(DOWNLOAD_SERVICE) as DownloadManager)
        // Initialize the selected pack
        val prefsName = getString(R.string.packsPrefName)
        val selectedPrefName = getString(R.string.selectedPackPrefName)
        viewModel.selectedPackName.value ?:
            context?.apply { with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
                viewModel.selectedPackName.value = getString(selectedPrefName, null)
            } }
        viewModel.downloads.value?.isEmpty() ?: viewModel.fetchManifest()
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
        // This seems like a crutch, but it's simpler than other solutions I've found to this
        // problem, which is that updating items in a LiveData<List> does *not* notify observers of
        // that list
        viewModel.listNeedsUpdate.observe(viewLifecycleOwner, Observer {listNeedsUpdate ->
            if (listNeedsUpdate) {
                (binding.downloadsList.adapter as DownloadsAdapter).notifyDataSetChanged()
                viewModel.listNeedsUpdate.value = false
            }
        })
        // When the selected pack changes, save it in preferences
        viewModel.selectedPackName.observe(viewLifecycleOwner, Observer { packName ->
            context?.apply {
                with(getSharedPreferences(getString(R.string.packsPrefName), Context.MODE_PRIVATE)) {
                    edit {
                        putString(getString(R.string.selectedPackPrefName), packName)
                    }
                }
            }
        })
        viewModel.downloads.observe(viewLifecycleOwner, Observer { viewModel.checkPacksDownloaded() })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).setToolbar(view.findViewById<Toolbar>(R.id.toolbar))
        // This is roughly how they make an adapter that references the view model in
        // https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksFragment.kt
        binding.downloadsList.adapter = DownloadsAdapter(viewModel = viewModel)
    }

//    companion object {
//        /**
//         * Use this factory method to create a new instance of
//         * this fragment using the provided parameters.
//         *
//         * @param param1 Parameter 1.
//         * @param param2 Parameter 2.
//         * @return A new instance of fragment DownloadsFragment.
//         */
//        @JvmStatic
//        fun newInstance(param1: String, param2: String) =
//            DownloadsFragment().apply {
//                arguments = Bundle().apply {
//                    putString(ARG_PARAM1, param1)
//                    putString(ARG_PARAM2, param2)
//                }
//            }
//    }
}
