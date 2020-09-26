package rocks.underfoot.underfootandroid.rocks

import android.util.Log
import android.view.View
import androidx.databinding.BindingAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior

object RocksBindings {
    // Slightly awkward way of binding the BottomSheet layout to the view model in a way that allows
    // clicks to expand or collapse it. Here context is the context panel. Adapted from
    // https://stackoverflow.com/a/47530511
    @BindingAdapter("requestedDetailState")
    @JvmStatic fun requestedContextState(view: View, showDetail: Boolean?) {
        showDetail ?: return
        val viewBottomSheetBehavior = BottomSheetBehavior.from<View>(view)
        viewBottomSheetBehavior.state = if (showDetail) {
            BottomSheetBehavior.STATE_EXPANDED
        } else {
            BottomSheetBehavior.STATE_COLLAPSED
        }
    }
}
