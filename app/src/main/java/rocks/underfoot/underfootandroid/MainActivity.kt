package rocks.underfoot.underfootandroid

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navController = findNavController(R.id.nav_host_fragment)
        findViewById<NavigationView>(R.id.nav_view)
            .setupWithNavController(navController)
    }

    // Set the toolbar so each fragment can define (and style) its own. Loosely based on
    // https://stackoverflow.com/a/35677316. Note that I'm not sure what the  memory implications
    // of constantly adding new toolbars on the drawer layout. I'm assuming they get destroyed
    // along with their fragments
    fun setToolbar(toolbar: Toolbar) {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_rocks,
            R.id.nav_downloads
        ), drawerLayout)
        val navController = findNavController(R.id.nav_host_fragment)
        toolbar.setupWithNavController(navController, appBarConfiguration)
    }
}