package rocks.underfoot.underfootandroid

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Set the graph start destination based on stored preference
        // https://stackoverflow.com/a/53992737
        val navController = findNavController(R.id.nav_host_fragment)
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        val prefsName = "underfoot"
        val lastDestinationPrefName = getString(R.string.lastDestinationPrefName)
        apply { with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
            graph.setStartDestination(
                try {
                    getInt(lastDestinationPrefName, R.id.nav_rocks)
                } catch (e:java.lang.ClassCastException) {
                    R.id.nav_rocks
                }
            )
        } }
        navController.graph = graph
        // Store the nav destination on change
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val apply = apply {
                with(getSharedPreferences(prefsName, Context.MODE_PRIVATE)) {
                    edit { putInt(lastDestinationPrefName, dest.id) }
                }
            }
        }
        // Set up the drawer menu
        findViewById<NavigationView>(R.id.nav_view)
            .setupWithNavController(navController)
    }

    // Set the toolbar so each fragment can define (and style) its own. Loosely based on
    // https://stackoverflow.com/a/35677316. Note that I'm not sure what the  memory implications
    // of constantly adding new toolbars on the drawer layout. I'm assuming they get destroyed
    // along with their fragments
    fun setToolbar(toolbar: Toolbar, navigationIcon: Drawable? = null) {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_rocks,
            R.id.nav_water,
            R.id.nav_downloads
        ), drawerLayout)
        val navController = findNavController(R.id.nav_host_fragment)
        toolbar.setupWithNavController(navController, appBarConfiguration)
        navigationIcon?.let {
            toolbar.navigationIcon = it
        }
    }
}
