package rocks.underfoot.underfootandroid.rocks

import android.util.Log
import android.view.View
import androidx.databinding.BindingAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior

object RocksBindings {
    // Slightly awkward way of binding the BottomSheet layout to the view model in a way that allows
    // clicks to expand or collapse it. Adapted from https://stackoverflow.com/a/47530511
    @BindingAdapter("requestedDetailState")
    @JvmStatic fun requestedDetailState(view: View, showDetail: Boolean?) {
        showDetail ?: return
        val viewBottomSheetBehavior = BottomSheetBehavior.from<View>(view)
        viewBottomSheetBehavior.state = if (showDetail) {
            BottomSheetBehavior.STATE_EXPANDED
        } else {
            BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    // Boy, so many problems with this. First, if you want to pass a callback here, it must be a
    // lambda that takes a view as its first arg. Second, that lambda has to be a real lambda, not
    // a function in the view model. The view model function has to return a lambda. Ugh. I feel
    // like there also a potential problem with adding the callback here without removing it, but
    // it seems to work.
    @BindingAdapter("onDetailChange")
    @JvmStatic fun onDetailChange(view: View, callback: (v: View, shown: Boolean) -> Unit) {
        val viewBottomSheetBehavior = BottomSheetBehavior.from<View>(view)
        viewBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> callback(view,true)
                    BottomSheetBehavior.STATE_COLLAPSED -> callback(view,false)
                    else -> {
                        // Do nothing. This handler will get called while dragging as well as when
                        // it's fully open or closed, so we don't want to be constantly updating the
                        // view model during that process
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }
}
