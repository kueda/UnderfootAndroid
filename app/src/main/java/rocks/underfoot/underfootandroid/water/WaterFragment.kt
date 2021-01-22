package rocks.underfoot.underfootandroid.water

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.mapzen.tangram.MapView
import rocks.underfoot.underfootandroid.R
import rocks.underfoot.underfootandroid.databinding.FragmentWaterBinding
import rocks.underfoot.underfootandroid.maptuils.MapFragment

class WaterFragment : MapFragment() {

    companion object {
        fun newInstance() = WaterFragment()
    }

    private lateinit var binding: FragmentWaterBinding

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