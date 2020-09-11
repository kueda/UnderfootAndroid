package rocks.underfoot.underfootandroid.downloads

import android.util.Log
import android.widget.ListView
import androidx.databinding.BindingAdapter

// Based on the strategy described at http://blog.trsquarelab.com/2016/01/data-binding-in-android-listview.html
// and demonstrated in https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksListBindings.kt
// This business of custom view tag attributes and their bindings is a bit weird. Documented at
// https://developer.android.com/topic/libraries/data-binding/binding-adapters. One of the
// strangest things to me is that the annotation could actually be anywhere, this object is just a
// reasonable place to put it.
object DownloadsListBindings {
    val tag = "DownloadsListBindings"

    // The annotation value is the name of the attribute used in the layout, i.e.
    // app:underfootPacks="@{theList}". I'm using the awkward underfootPacks to make to super clear that this is a custom attribute
    @BindingAdapter("bind:downloads")
    @JvmStatic fun setUnderfootDownloads(listView: ListView, items: List<Download>?) {
        val newDownloads = items ?: listOf<Download>()
        with(listView.adapter as DownloadsAdapter) {
            updateDownloads(newDownloads)
        }
    }
}
