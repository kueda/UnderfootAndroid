package rocks.underfoot.underfootandroid

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.mapzen.tangram.*

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
    MapController.SceneLoadListener,
//    TouchInput.TapResponder,
//    TouchInput.DoubleTapResponder,
//    FeaturePickListener,
//    TouchInput.RotateResponder,
    MapView.MapReadyCallback {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private val TAG = "RocksFragment"

    private val SCENE_FILE_PATH = "asset:///usgs-state-color-scene.yml"
    private val UNIT_AGE_SCENE_FILE_PATH = "asset:///unit-age-scene.yml"
    private val SPAN_COLOR_SCENE_FILE_PATH = "asset:///span-color.yml"

    private val FILES = listOf(
        // From https://github.com/kueda/underfoot
        // "underfoot_units-20191124.mbtiles",
        // "underfoot_ways-20190912.mbtiles",
        // "elevation-20190408.mbtiles"
        "rocks-20200509.mbtiles",
        "ways-20200509.mbtiles",
        "contours-20200509.mbtiles"

        // // small one for download testing
        // "underfoot-20180401-14.mbtiles"
    )

    private lateinit var mapView: MapView
    private lateinit var mapController: MapController

    private var zoom = 10.0F
    private var lng = -122.24
    private var lat = 37.73

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_rocks, container, false)
        mapView = root.findViewById<MapView>(R.id.map);
        mapView.getMapAsync(this);
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
//        This isn't working for some reason
//        val color = ContextCompat.getColor(requireContext(), R.color.transBlack)
//        toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.menu_white_24dp)
//        toolbar.navigationIcon?.setTint(color)
        (activity as MainActivity).setToolbar(toolbar)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RocksFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            RocksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

//    Tangram methods
    override fun onSceneReady(sceneId: Int, sceneError: SceneError?) {
        if (sceneError != null) {
            Log.d(TAG, "Scene update errors ${sceneError.getSceneUpdate()} ${sceneError.getError()}")
            return
        }
        val pos = LngLat(lng, lat);
        mapController.updateCameraPosition(CameraUpdateFactory.newLngLatZoom(pos, zoom), 500)
    }

    override fun onMapReady(mc: MapController?) {
        mapController = mc!!
        mapController.setSceneLoadListener(this);
        mapController.loadSceneFile(SCENE_FILE_PATH);

    }
}
