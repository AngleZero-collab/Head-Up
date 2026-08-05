package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.poselandmarker.fragment.PermissionsFragment
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var suppressBackgroundGuardResume = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Toast.makeText(
                this,
                if (granted) R.string.notification_permission_granted else R.string.notification_permission_denied,
                Toast.LENGTH_SHORT,
            ).show()
            checkAndPromptOverlayPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        playLaunchAnimation()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.fragment_container) as NavHostFragment
        navController = navHost.navController
        binding.navigation.setupWithNavController(navController)
        binding.navigation.setOnItemReselectedListener { }
        configureNavigationChrome()

        binding.notificationButton.setOnClickListener { openNotificationControls() }
        binding.petOverlayButton.setOnClickListener { togglePetOverlay() }
        binding.stopGuardButton.setOnClickListener { toggleBackgroundGuard() }
        binding.settingsButton.setOnClickListener { showSettingsDialog() }
        updateGuardButton()
        updatePetOverlayButton()
        PostureSyncScheduler.schedulePeriodic(this)
        initPermissionFlow()
    }

    fun startHeadUpService(action: String = HeadUpService.ACTION_PAUSE_CAMERA) {
        if (!HeadUpRepository.isBackgroundGuardEnabled(this)) return
        if (!PermissionsFragment.hasPermissions(this)) return
        ContextCompat.startForegroundService(
            this,
            Intent(this, HeadUpService::class.java).setAction(action),
        )
    }

    fun shouldResumeBackgroundGuard(): Boolean =
        !suppressBackgroundGuardResume && HeadUpRepository.isBackgroundGuardEnabled(this)

    private fun playLaunchAnimation() {
        binding.splashLogo.apply {
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
            translationY = 24f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(650L)
                .start()
        }
        binding.splashStatus.apply {
            alpha = 0f
            animate().alpha(1f).setStartDelay(280L).setDuration(400L).start()
        }
        binding.splashOverlay.postDelayed({
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(420L)
                .withEndAction { binding.splashOverlay.visibility = View.GONE }
                .start()
        }, 1_250L)
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            getString(R.string.settings_account, HeadUpAuthStore.userLabel(this)),
            getString(R.string.settings_sync_now),
            getString(R.string.settings_language),
            getString(R.string.settings_overlay),
            getString(R.string.settings_calibration),
            getString(R.string.settings_data_management),
            getString(R.string.settings_logout),
            getString(R.string.settings_reset_data),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setItems(items) { _, index ->
                when (index) {
                    0 -> showAccountDialog()
                    1 -> enqueueManualSync()
                    2 -> showLanguagePicker()
                    3 -> openOverlaySettingsIfNeeded()
                    4 -> navigateToCalibration()
                    5 -> showDataManagementDialog()
                    6 -> confirmLogout()
                    7 -> confirmResetData()
                }
            }
            .show()
    }

    private fun showAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.account_title)
            .setMessage(
                getString(
                    R.string.account_message,
                    HeadUpAuthStore.currentUserId(this),
                    HeadUpAuthStore.role(this),
                    HeadUpAuthStore.subscriptionTier(this),
                ),
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun enqueueManualSync() {
        PostureSyncScheduler.enqueueOneTime(this)
        Toast.makeText(this, R.string.sync_queued, Toast.LENGTH_SHORT).show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                HeadUpAuthStore.clearSession(this)
                suppressBackgroundGuardResume = true
                HeadUpRepository.setForegroundScanActive(this, false)
                stopService(Intent(this, HeadUpService::class.java))
                navigateToLogin()
                Toast.makeText(this, R.string.logout_complete, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun navigateToLogin() {
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.id, true)
            .build()
        navController.navigate(R.id.login_fragment, null, options)
    }

    private fun configureNavigationChrome() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showAppChrome = destination.id != R.id.login_fragment &&
                destination.id != R.id.permissions_fragment
            if (showAppChrome) {
                suppressBackgroundGuardResume = false
            }
            binding.headupTopBar.visibility = if (showAppChrome) View.VISIBLE else View.GONE
            binding.navigationShell.visibility = if (showAppChrome) View.VISIBLE else View.GONE
            updateGuardButton()
            updatePetOverlayButton()
        }
    }

    private fun toggleBackgroundGuard() {
        val enabled = HeadUpRepository.isBackgroundGuardEnabled(this)
        if (enabled) {
            HeadUpRepository.setBackgroundGuardEnabled(this, false)
            suppressBackgroundGuardResume = true
            stopService(Intent(this, HeadUpService::class.java))
            Toast.makeText(this, R.string.guard_stopped, Toast.LENGTH_SHORT).show()
        } else {
            HeadUpRepository.setBackgroundGuardEnabled(this, true)
            suppressBackgroundGuardResume = false
            val action = if (HeadUpRepository.isForegroundScanActive(this)) {
                HeadUpService.ACTION_PAUSE_CAMERA
            } else {
                HeadUpService.ACTION_RESUME_CAMERA
            }
            startHeadUpService(action)
            Toast.makeText(this, R.string.guard_started, Toast.LENGTH_SHORT).show()
        }
        updateGuardButton()
    }

    private fun togglePetOverlay() {
        val enabled = HeadUpRepository.isPetOverlayEnabled(this)
        HeadUpRepository.setPetOverlayEnabled(this, !enabled)
        if (enabled) {
            Toast.makeText(this, R.string.pet_overlay_disabled, Toast.LENGTH_SHORT).show()
        } else {
            if (!Settings.canDrawOverlays(this)) {
                openOverlaySettingsIfNeeded()
            } else {
                val action = if (HeadUpRepository.isForegroundScanActive(this)) {
                    HeadUpService.ACTION_PAUSE_CAMERA
                } else {
                    HeadUpService.ACTION_RESUME_CAMERA
                }
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, HeadUpService::class.java).setAction(action),
                )
            }
            Toast.makeText(this, R.string.pet_overlay_enabled, Toast.LENGTH_SHORT).show()
        }
        updatePetOverlayButton()
    }

    private fun updateGuardButton() {
        if (!::binding.isInitialized) return
        val enabled = HeadUpRepository.isBackgroundGuardEnabled(this)
        binding.stopGuardButton.setImageResource(
            if (enabled) R.drawable.ic_stop_headup else R.drawable.ic_play_headup,
        )
        binding.stopGuardButton.contentDescription = getString(
            if (enabled) R.string.guard_stop else R.string.guard_start,
        )
    }

    private fun updatePetOverlayButton() {
        if (!::binding.isInitialized) return
        val enabled = HeadUpRepository.isPetOverlayEnabled(this)
        binding.petOverlayButton.alpha = if (enabled) 1f else 0.42f
        binding.petOverlayButton.contentDescription = getString(
            if (enabled) R.string.pet_overlay_disable else R.string.pet_overlay_enable,
        )
    }

    private fun showDataManagementDialog() {
        HeadUpRepository.getRecordStats(this) { count, unsynced, sizeKb ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(R.string.data_management_title)
                    .setMessage(getString(R.string.data_management_message, count, unsynced, sizeKb))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.export_csv) { _, _ -> exportDataToCsv() }
                    .show()
            }
        }
    }

    private fun exportDataToCsv() {
        HeadUpRepository.getAllRecordsAsCsv(this) { csvString ->
            runOnUiThread {
                try {
                    val file = File(cacheDir, "Head Up_Data_${System.currentTimeMillis()}.csv")
                    file.writeText(csvString)
                    val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, "Head Up Posture Data Export")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.export_csv)))
                } catch (error: Exception) {
                    Toast.makeText(this, getString(R.string.export_failed, error.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToCalibration() {
        HeadUpRepository.requestCalibration(this)
        binding.navigation.selectedItemId = R.id.camera_fragment
        Toast.makeText(this, R.string.calibration_prepare, Toast.LENGTH_LONG).show()
    }

    private fun confirmResetData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_data_title)
            .setMessage(R.string.reset_data_message)
            .setPositiveButton(R.string.reset_data_confirm) { _, _ ->
                HeadUpRepository.resetAllData(this)
                updateGuardButton()
                Toast.makeText(this, R.string.reset_data_complete, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguagePicker() {
        val languageTags = arrayOf("en", "zh-TW")
        val labels = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_chinese),
        )
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selectedIndex = if (currentTag.startsWith("zh")) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(languageTags[which]),
                )
                dialog.dismiss()
            }
            .show()
    }

    private fun initPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionIfNeeded()
        } else {
            checkAndPromptOverlayPermission()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            checkAndPromptOverlayPermission()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.notification_permission_granted, Toast.LENGTH_SHORT).show()
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationControls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            },
        )
    }

    private fun checkAndPromptOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.overlay_permission_open) { _, _ -> openOverlaySettingsIfNeeded() }
            .setNegativeButton(R.string.overlay_permission_later, null)
            .show()
    }

    private fun openOverlaySettingsIfNeeded() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_enabled, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}
