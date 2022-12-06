package rocks.underfoot.underfootandroid.water

import android.database.sqlite.SQLiteCantOpenDatabaseException
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.mapzen.tangram.MapView
import com.mapzen.tangram.SceneUpdate
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentWaterBinding
import rocks.underfoot.underfootandroid.maptuils.MapFragment

class WaterFragment : MapFragment() {

    companion object {
        fun newInstance() = WaterFragment()
        const val TAG = "WaterFragmant"
    }

    private lateinit var binding: FragmentWaterBinding

    override fun navigateToDownloads() {
        findNavController().navigate(WaterFragmentDirections.actionNavWaterToNavDownloads())
    }

    override fun getToolbarID(): Int {
        return R.id.water_toolbar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(WaterViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_water,
            container,
            false
        )
        val waterViewModel = viewModel as WaterViewModel
        binding.viewModel = waterViewModel
        // Without this, the binding will not update when the view model updates
        binding.lifecycleOwner = viewLifecycleOwner
        mapResponder = WaterMapResponder(waterViewModel, viewLifecycleOwner,
            resources.getColor(R.color.colorAccent, null)
        )
        mapView = binding.root.findViewById<MapView>(R.id.map)
        mapView.getMapAsync(mapResponder)
        setupMap()
        waterViewModel.mbtilesPath.observe(viewLifecycleOwner, Observer {
            if (it.isNotBlank()) {
                try {
                    waterViewModel.repository = WaterRepository(it)
                } catch (e: SQLiteCantOpenDatabaseException) {
                    Log.d(TAG, "Failed to load $it")
                }
            }
        })
        val colorAccent = resources.getColor(R.color.colorAccent, null).toColorLong()
        waterViewModel.highlightSegments.observe(viewLifecycleOwner, Observer {highlightIds ->
            val newUpdates = if (highlightIds.isEmpty()) {
                listOf(SceneUpdate(
                    "layers.highlight_waterways.filter",
                    "function() { return false }"
                ))
            } else {
                val jsArray = "[${highlightIds.joinToString(",") { "'$it'" }}]"
                listOf(
                    SceneUpdate(
                        "layers.highlight_waterways.filter",
                        "function() { return $jsArray.indexOf(feature.source_id) >= 0; }"
                    ),
                    SceneUpdate(
                        "layers.highlight_waterways.draw.lines.color",
                        "[${colorAccent.red}, ${colorAccent.green}, ${colorAccent.blue}]",
                    ),
                )
            }
            waterViewModel.sceneUpdatesForSelectedPack.value?.let {updates ->
                mapResponder.mapController.loadSceneFile(
                    viewModel.sceneFilePath.value,
                    updates + newUpdates
                )
            }
        })
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(WaterViewModel::class.java)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        TODO("Not yet implemented")
    }

}