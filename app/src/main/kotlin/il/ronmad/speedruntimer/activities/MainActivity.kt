package il.ronmad.speedruntimer.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import il.ronmad.speedruntimer.*
import il.ronmad.speedruntimer.databinding.ActivityMainBinding
import il.ronmad.speedruntimer.fragments.GamesListFragment
import il.ronmad.speedruntimer.realm.Game
import il.ronmad.speedruntimer.realm.gameExists
import il.ronmad.speedruntimer.ui.InstalledAppsViewModel
import io.realm.Realm
import io.realm.exceptions.RealmException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    internal lateinit var viewBinding: ActivityMainBinding

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var realm: Realm

    private var rateSnackbarShown = false
    private var addGamesSnackbarShown = false

    private lateinit var viewModel: InstalledAppsViewModel
    private var installedGames: List<String> = emptyList()

    private lateinit var requestOverlayPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var requestNotificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Edge-to-edge with Material 3
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        setSupportActionBar(viewBinding.toolbar)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        setupRealm()

        viewModel = ViewModelProvider(this)[InstalledAppsViewModel::class]

        // Permission launchers
        requestOverlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            checkPermissionsAndProceed()
        }

        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Dialogs.showNotificationPermissionDialog(this) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            } else {
                checkPermissionsAndProceed()
            }
        }

        // Check permissions on first launch
        checkPermissionsAndProceed()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.setupDone.collect {
                    setupInstalledGamesList()
                    setupSnackbars()
                }
            }
        }
        viewModel.setupInstalledAppsMap()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GamesListFragment(), TAG_GAMES_LIST_FRAGMENT)
                .commit()
        }
    }

    /**
     * Checks if required permissions are granted.
     * If not, requests them before allowing timer usage.
     */
    private fun checkPermissionsAndProceed() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Dialogs.showOverlayPermissionRequiredDialog(this) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                requestOverlayPermissionLauncher.launch(intent)
            }
            return
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // All permissions granted — proceed normally
    }

    override fun onResume() {
        super.onResume()
        if (TimerService.IS_ACTIVE && !TimerService.isInPermissionFlow) {
            Dialogs.showCloseTimerOnResumeDialog(this) {
                val closeTimerIntent = Intent(getString(R.string.action_close_timer)).also {
                    it.putExtra(getString(R.string.extra_close_timer_from_onresume), true)
                }
                sendBroadcast(closeTimerIntent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        sharedPrefs.edit()
            .putInt(getString(R.string.key_launch_counter), launchCounter)
            .putBoolean(getString(R.string.key_rate_snackbar_shown), rateSnackbarShown)
            .putBoolean(getString(R.string.key_add_games_snackbar_shown), addGamesSnackbarShown)
            .apply()
        FSTWidget.forceUpdateWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity_actions, menu)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            menu.findItem(R.id.menu_add_games).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.menu_add_games -> {
                addInstalledGames()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRealm() {
        val savedData = sharedPrefs.getString(getString(R.string.key_games), "").orEmpty()
        if (savedData.isEmpty()) {
            realm = Realm.getDefaultInstance()
        } else {
            Realm.deleteRealm(Realm.getDefaultConfiguration()!!)
            realm = Realm.getDefaultInstance()
            try {
                realm.executeTransaction {
                    realm.createAllFromJson(Game::class.java, savedData)
                }
            } catch (e: RealmException) {
                realm.executeTransaction {
                    realm.createAllFromJson(Game::class.java, Util.migrateJson(savedData))
                }
            }
            sharedPrefs.edit()
                .remove(getString(R.string.key_games))
                .apply()
        }
    }

    private fun setupInstalledGamesList() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            installedGames = app?.installedAppsMap.orEmpty().values
                .filter { it.category == ApplicationInfo.CATEGORY_GAME }
                .map { packageManager.getApplicationLabel(it).toString() }
        }
    }

    private fun setupSnackbars() {
        var toShowRateSnackbar = false
        rateSnackbarShown = sharedPrefs.getBoolean(getString(R.string.key_rate_snackbar_shown), false)
        if (!rateSnackbarShown && launchCounter == 0 && !realm.isEmpty) {
            val savedLaunchCounter = sharedPrefs.getInt(getString(R.string.key_launch_counter), 0)
            launchCounter = (savedLaunchCounter + 1).coerceAtMost(4)
            toShowRateSnackbar = launchCounter == 3
        }

        addGamesSnackbarShown = sharedPrefs.getBoolean(getString(R.string.key_add_games_snackbar_shown), false)
        val toShowAddGamesSnackbar = getAvailableInstalledGames().isNotEmpty() && !addGamesSnackbarShown

        if (toShowAddGamesSnackbar) {
            Handler(Looper.getMainLooper()).postDelayed(::showAddInstalledGamesSnackbar, 1000)
            addGamesSnackbarShown = true
        } else if (toShowRateSnackbar) {
            Handler(Looper.getMainLooper()).postDelayed(::showRateSnackbar, 1000)
            rateSnackbarShown = true
        }
    }

    private fun showRateSnackbar() {
        val snackbar = Snackbar.make(
            viewBinding.fabAdd, getString(R.string.rate_snackbar),
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction(R.string.rate) {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
            try {
                startActivity(marketIntent)
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            }
        }
        snackbar.show()
    }

    private fun showAddInstalledGamesSnackbar() {
        val snackbar = Snackbar.make(viewBinding.fabAdd, getString(R.string.add_games_snackbar), Snackbar.LENGTH_LONG)
        snackbar.setAction(R.string.add) { addInstalledGames() }
        snackbar.show()
    }

    private fun getAvailableInstalledGames() = installedGames.filter { !realm.gameExists(it) }

    private fun addInstalledGames() {
        val gameNames = getAvailableInstalledGames()
        if (gameNames.isEmpty()) {
            showToast(getString(R.string.no_games_to_add))
        } else {
            Dialogs.showAddInstalledGamesDialog(this, realm, gameNames) {
                (supportFragmentManager.findFragmentByTag(TAG_GAMES_LIST_FRAGMENT) as? GamesListFragment)
                    ?.refreshList()
                Snackbar.make(viewBinding.fabAdd, "Games added", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private var launchCounter = 0
    }
}
