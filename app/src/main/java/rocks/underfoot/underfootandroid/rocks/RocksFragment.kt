package rocks.underfoot.underfootandroid.rocks

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.mapzen.tangram.*
import rocks.underfoot.underfootandroid.MainActivity
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentRocksBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [RocksFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class RocksFragment : Fragment(),
//    MapController.SceneLoadListener,
//    TouchInput.TapResponder,
//    TouchInput.DoubleTapResponder,
//    FeaturePickListener,
//    TouchInput.RotateResponder,
    MapView.MapReadyCallback,
    LifecycleObserver
{
//    // TODO: Rename and change types of parameters
//    private var param1: String? = null
//    private var param2: String? = null

    companion object {
        private const val TAG = "RocksFragment"
        private const val REQUEST_ACCESS_FINE_LOCATION_CODE = 1;
    }

    private lateinit var viewModel: RocksViewModel
    private lateinit var binding: FragmentRocksBinding
    private lateinit var mapView: MapView

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
//        arguments?.let {
//            param1 = it.getString(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
//        }
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
        mapView = binding.root.findViewById<MapView>(R.id.map);
        mapView?.getMapAsync(this);
        viewModel.selectedPackName.observe(viewLifecycleOwner, Observer {
            if (it.isNullOrEmpty()) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.map_no_data_title))
                    .setMessage(getString(R.string.map_no_data_description))
                    .setPositiveButton(getString(R.string.choose_downloads), DialogInterface.OnClickListener { _, _ ->
                        findNavController().navigate(RocksFragmentDirections.actionNavRocksToNavDownloads())
                    })
                    .create().show()
            }
        })
        val prefsName = getString(R.string.packsPrefName)
        val selectedPrefName = getString(R.string.selectedPackPrefName)
        context?.apply { with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
            viewModel.selectedPackName.value = getString(selectedPrefName, "")
        } }
        viewModel.locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE)
                as LocationManager
        viewModel.requestingLocationUpdates.observe(viewLifecycleOwner, Observer {requestingLocationUpdates ->
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
                            .setPositiveButton("Grant Permission", DialogInterface.OnClickListener { _, _ ->
                                requestFineLocationPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                            })
                            .create().show()
                    }
                    else -> {
                        // You can directly ask for the permission.
                        // The registered ActivityResultCallback gets the result of this request.
                        requestFineLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_FINE_LOCATION)
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
        view?.findViewById<Toolbar>(R.id.toolbar)?.let{ toolbar ->
            //        This isn't working for some reason
//            val color = ContextCompat.getColor(requireContext(), R.color.transBlack)
//            toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.menu_white_24dp)
//            toolbar.navigationIcon?.setTint(color)
            (activity as MainActivity).setToolbar(toolbar)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onMapReady(mc: MapController?) {
        viewModel.onMapReady(mc!!)
    }

//    companion object {
//        /**
//         * Use this factory method to create a new instance of
//         * this fragment using the provided parameters.
//         *
//         * @param param1 Parameter 1.
//         * @param param2 Parameter 2.
//         * @return A new instance of fragment RocksFragment.
//         */
//        // TODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance(param1: String, param2: String) =
//            RocksFragment().apply {
//                arguments = Bundle().apply {
//                    putString(ARG_PARAM1, param1)
//                    putString(ARG_PARAM2, param2)
//                }
//            }
//    }
}
