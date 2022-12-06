package rocks.underfoot.underfootandroid.rocks

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.mapzen.tangram.MapView
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentRocksBinding
import rocks.underfoot.underfootandroid.maptuils.MapFragment

class RocksFragment : MapFragment() {

    companion object {
        private const val TAG = "RocksFragment"
    }

    private lateinit var binding: FragmentRocksBinding

    override fun navigateToDownloads() {
        Log.d(TAG, "navigateToDownloads")
        findNavController().navigate(RocksFragmentDirections.actionNavRocksToNavDownloads())
    }

    override fun getToolbarID(): Int {
        return R.id.rocks_toolbar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[RocksViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_rocks,
            container,
            false
        )
        val rocksViewModel = viewModel as RocksViewModel
        binding.viewModel = rocksViewModel
        // Without this, the binding will not update when the view model updates
        binding.lifecycleOwner = viewLifecycleOwner
        mapResponder = RocksMapResponder(rocksViewModel, viewLifecycleOwner,
            resources.getColor(R.color.colorAccent, null)
        )
        mapView = binding.root.findViewById<MapView>(R.id.map)
        mapView?.getMapAsync(mapResponder)
        setupMap()
        rocksViewModel.rocksMbtilesPath.observe(viewLifecycleOwner, Observer {
            if (it.isNotBlank()) {
                try {
                    rocksViewModel.repository = RockUnitsRepository(it)
                } catch (e: android.database.sqlite.SQLiteCantOpenDatabaseException) {
                    Log.d(TAG, "Failed to load $it")
                }
            }
        })
        return binding.root
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        val rocksViewModel = viewModel as RocksViewModel
        rocksViewModel.sceneFilePath.value = when (item?.itemId) {
            R.id.map_activity_layer_menu_lithology -> RocksMapResponder.SCENE_FILE_PATH
            R.id.map_activity_layer_menu_age -> RocksMapResponder.UNIT_AGE_SCENE_FILE_PATH
            R.id.map_activity_layer_menu_span -> RocksMapResponder.SPAN_COLOR_SCENE_FILE_PATH
            else -> return false
        }
        return true
    }
}
