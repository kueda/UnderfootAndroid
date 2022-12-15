package rocks.underfoot.underfootandroid

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import rocks.underfoot.underfootandroid.downloads.DownloadsFragment
import rocks.underfoot.underfootandroid.rocks.RocksFragment
import rocks.underfoot.underfootandroid.setting.SettingsActivity
import rocks.underfoot.underfootandroid.water.WaterFragment

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var navigationView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializers()

        drawerMenuItem()

        drawerManager()

        replaceFragment(RocksFragment())
    }

    private fun initializers() {
        navigationView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
    }

    private fun drawerMenuItem() {
//        this drawer menu item help to easily manage the drawer menu item
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_rocks -> {
                    replaceFragment(RocksFragment())
                    true
                }
                R.id.nav_water -> {
                    replaceFragment(WaterFragment())
                    true
                }
                R.id.nav_downloads -> {
                    replaceFragment(DownloadsFragment())
                    true
                }
                R.id.id_menu_setting -> {
                    startActivity(Intent(applicationContext, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    fun setToolbar(toolbar: Toolbar, navigationIcon: Drawable? = null) {
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_rocks,
                R.id.nav_water,
                R.id.nav_downloads
            ), drawerLayout
        )
        toolbar.setupWithNavController(navController, appBarConfiguration)
        navigationIcon?.let {
            toolbar.navigationIcon = it
        }
    }

    private fun drawerManager() {
        // drawer layout instance to toggle the menu icon to open
        // drawer and back button to close drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        actionBarDrawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close)

        // pass the Open and Close toggle for the drawer layout listener
        // to toggle the button
        drawerLayout.addDrawerListener(actionBarDrawerToggle)

        // to make the Navigation drawer icon always appear on the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        actionBarDrawerToggle.syncState()
    }

    private fun navigateToFragment(fragment: Fragment) {
//        this navigate to another fragment while adding previous fragment to backstack
        supportFragmentManager.beginTransaction().add(R.id.nav_host_fragment, fragment).commit()
    }

    private fun replaceFragment(fragment: Fragment) {
//        this replace the current fragment with a new fragment while destroying the previous fragment
        supportFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, fragment).commit()
    }
}
