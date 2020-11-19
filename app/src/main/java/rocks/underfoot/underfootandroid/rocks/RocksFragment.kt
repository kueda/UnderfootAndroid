package rocks.underfoot.underfootandroid.rocks

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.convertTo
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.mapzen.tangram.CameraUpdateFactory
import com.mapzen.tangram.LngLat
import com.mapzen.tangram.MapView
import rocks.underfoot.underfootandroid.MainActivity
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentRocksBinding
import kotlin.math.max
import kotlin.math.min

class RocksFragment : Fragment(), LifecycleObserver, Toolbar.OnMenuItemClickListener {

    companion object {
        private const val TAG = "RocksFragment"
        private const val REQUEST_ACCESS_FINE_LOCATION_CODE = 1;
        private const val MAP_PREFS = "map"
        private const val MAP_PREFS_LAT = "lat"
        private const val MAP_PREFS_LNG = "lng"
        private const val MAP_PREFS_ZOOM = "zoom"
        private const val MAX_ZOOM = 14.9f
    }

    private lateinit var viewModel: RocksViewModel
    private lateinit var binding: FragmentRocksBinding
    private lateinit var mapView: MapView
    private lateinit var mapResponder: RocksMapResponder

    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.requestingLocationUpdates.value = true
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                AlertDialog.Builder(requireActivity())
                    .setTitle("Got It")
                    .setMessage("FYI, if you want to view your current location, you will need to grant that permission")
                    .create().show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(RocksViewModel::class.java)
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
        binding.viewModel = viewModel
        // Without this, the binding will not update when the view model updates
        binding.lifecycleOwner = viewLifecycleOwner
        mapResponder = RocksMapResponder(viewModel, viewLifecycleOwner,
            resources.getColor(R.color.colorAccent, null))
        mapView = binding.root.findViewById<MapView>(R.id.map)
        mapView?.getMapAsync(mapResponder)
        viewModel.selectedPackName.observe(viewLifecycleOwner, Observer {
            if (it.isNullOrEmpty()) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.map_no_data_title))
                    .setMessage(getString(R.string.map_no_data_description))
                    .setPositiveButton(
                        getString(R.string.choose_downloads),
                        DialogInterface.OnClickListener { _, _ ->
                            findNavController().navigate(RocksFragmentDirections.actionNavRocksToNavDownloads())
                        })
                    .create().show()
            }
        })
        viewModel.rocksMbtilesPath.observe(viewLifecycleOwner, Observer {
            if (it.isNotBlank()) {
                try {
                    viewModel.repository = RockUnitsRepository(it)
                } catch (e: android.database.sqlite.SQLiteCantOpenDatabaseException) {
                    Log.d(TAG, "Failed to load $it")
                }
            }
        })
        val prefsName = getString(R.string.packsPrefName)
        val selectedPrefName = getString(R.string.selectedPackPrefName)
        context?.apply {
            with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
                viewModel.selectedPackName.value = getString(selectedPrefName, "")
            }
            with(getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE)) {
                val lat = getFloat(MAP_PREFS_LAT, 0f).toDouble()
                val lng = getFloat(MAP_PREFS_LNG, 0f).toDouble()
                val zoom = getFloat(MAP_PREFS_ZOOM, 0f)
                if (lat != 0.0 && lng != 0.0 && zoom != 0f) {
                    Log.d(
                        TAG,
                        "loaded last pos from prefs ($zoom/$lng/$lat), setting the cameraUpdate"
                    )
                    viewModel.initialCameraUpdate.value = CameraUpdateFactory.newLngLatZoom(
                        LngLat(lng, lat),
                        zoom
                    )
                }
            }
        }
        viewModel.locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE)
                as LocationManager
        viewModel.requestingLocationUpdates.observe(
            viewLifecycleOwner,
            Observer { requestingLocationUpdates ->
                if (requestingLocationUpdates) {
                    when {
                        ContextCompat.checkSelfPermission(
                            requireActivity(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // You can use the API that requires the permission.
                            viewModel.startGettingLocation()
                        }
                        shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                            // In an educational UI, explain to the user why your app requires this
                            // permission for a specific feature to behave as expected. In this UI,
                            // include a "cancel" or "no thanks" button that allows the user to
                            // continue using your app without granting the permission.
                            AlertDialog.Builder(requireActivity())
                                .setTitle("Permission Required")
                                .setMessage("Underfoot needs your permission to retrieve your current location")
                                .setPositiveButton(
                                    "Grant Permission",
                                    DialogInterface.OnClickListener { _, _ ->
                                        requestFineLocationPermissionLauncher.launch(
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    })
                                .create().show()
                        }
                        else -> {
                            // You can directly ask for the permission.
                            // The registered ActivityResultCallback gets the result of this request.
                            requestFineLocationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        }
                    }
                }
            })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.lifecycle?.addObserver(this)
    }

    // https://stackoverflow.com/a/62076948
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreated() {
        activity?.lifecycle?.removeObserver(this)
        view?.findViewById<Toolbar>(R.id.toolbar)?.let {
            it.setOnMenuItemClickListener(this)
            (activity as MainActivity).setToolbar(it)
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        context?.apply { with(getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE)) {
            viewModel.cameraPosition.value?.let { cameraPosition ->
                Log.d(TAG, "cameraPosition exists")
                edit {
                    putFloat(MAP_PREFS_LAT, cameraPosition.position.latitude.toFloat())
                    putFloat(MAP_PREFS_LNG, cameraPosition.position.longitude.toFloat())
                    putFloat(MAP_PREFS_ZOOM, min(cameraPosition.zoom, MAX_ZOOM))
                }
            }
        } }
        super.onPause()
        mapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView")
        mapResponder.onDestroyView()
        mapView.onDestroy()
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        viewModel.sceneFilePath.value = when (item?.itemId) {
            R.id.map_activity_layer_menu_lithology -> RocksMapResponder.SCENE_FILE_PATH
            R.id.map_activity_layer_menu_age -> RocksMapResponder.UNIT_AGE_SCENE_FILE_PATH
            R.id.map_activity_layer_menu_span -> RocksMapResponder.SPAN_COLOR_SCENE_FILE_PATH
            else -> return false
        }
        return true
    }
}
