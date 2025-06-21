package com.example.m867

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.m867.Score.ProfileFragment
import com.example.m867.Score.ScoreRepository
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private var profileFragment: ProfileFragment? = null
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var scoreRepository: ScoreRepository

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        scoreRepository = ScoreRepository(this)

        lifecycleScope.launch {
            scoreRepository.syncScores(auth.currentUser?.uid ?: "")
        }

        if (auth.currentUser == null) {
            startAuthActivity()
            return
        }

        checkNotificationPermission()
        setupViewPager()
    }

    fun updateScoreDisplay() {
        val userId = auth.currentUser?.uid ?: return
        val points = scoreRepository.getCurrentScore(userId)
        if (profileFragment?.isAdded == true) {
            profileFragment?.updateScoreDisplay(points)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showNotificationPermissionDialog()
            } else {
                scheduleNotification()
            }
        } else {
            scheduleNotification()
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Разрешение уведомлений")
            .setMessage("Для получения напоминаний разрешите показ уведомлений")
            .setPositiveButton("Настройки") { _, _ ->
                openAppNotificationSettings()
            }
            .setNegativeButton("Пропустить") { _, _ ->
                showPermissionDeniedMessage("Вы не будете получать напоминания")
            }
            .setCancelable(false)
            .show()
    }

    private fun scheduleNotification() {
        notificationScheduler = NotificationScheduler()
        notificationScheduler.scheduleNotification(this)
    }

    private fun setupViewPager() {
        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)

        viewPager.adapter = ViewPagerAdapter(this).apply {
            onProfileFragmentCreated = { fragment ->
                profileFragment = fragment
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Задачи"
                1 -> "Таймер"
                2 -> "Привычки"
                3 -> "Профиль"
                else -> null
            }
        }.attach()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    scheduleNotification()
                } else {
                    showPermissionDeniedMessage("Уведомления отключены")
                }
            }
        }
    }

    private fun showPermissionDeniedMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    fun updateHabitStats() {
        profileFragment?.refreshHabitStats()
    }

    private fun startAuthActivity() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}