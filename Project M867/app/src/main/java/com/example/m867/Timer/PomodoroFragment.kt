package com.example.m867.Timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.m867.MainActivity
import com.example.m867.R
import com.example.m867.Score.ScoreRepository
import com.example.m867.databinding.FragmentPomodoroBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class PomodoroFragment : Fragment() {

    private var _binding: FragmentPomodoroBinding? = null
    private val binding get() = _binding!!

    private lateinit var alarmManager: AlarmManager
    private lateinit var pendingIntent: PendingIntent
    private var timer: CountDownTimer? = null
    private var timeLeftInMillis = 0L
    private var isRunning = false
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var scoreRepository: ScoreRepository
    private lateinit var userId: String
    private lateinit var timerRepository: TimerRepository
    private lateinit var btnStartReset: MaterialButton
    private lateinit var btnStopSound: MaterialButton

    private var distractionTicketsUsed = 0
    private var isDistractionActive = false
    private var distractionTimer: CountDownTimer? = null
    private var distractionMinutes = 0

    private var currentPhase = Phase.WORK
    private var isTimerFinished = false

    private var remainingBreakTime = 0L


    enum class Phase {
        WORK, BREAK
    }

    override fun onCreateView(

        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPomodoroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updatePickersState()
        super.onViewCreated(view, savedInstanceState)
        scoreRepository = ScoreRepository(requireContext())
        timerRepository = TimerRepository(requireContext())
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        progressIndicator = binding.progressIndicator
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.timer_sound)
        btnStartReset = binding.btnStartReset
        btnStopSound = binding.btnStopSound

        // Настройка NumberPicker для работы
        binding.npWork.minValue = 1
        binding.npWork.maxValue = 50
        binding.npWork.value = 25
        binding.npWork.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        // Настройка NumberPicker для перерыва
        binding.npBreak.minValue = 1
        binding.npBreak.maxValue = 30
        binding.npBreak.value = 5
        binding.npBreak.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        binding.npWork.setOnValueChangedListener { _, _, newVal ->
            if (currentPhase == Phase.WORK && !isRunning) {
                timeLeftInMillis = newVal * 60 * 1000L
                updateTimerText()
            }
        }

        binding.npBreak.setOnValueChangedListener { _, _, newVal ->
            if (currentPhase == Phase.BREAK && !isRunning) {
                timeLeftInMillis = newVal * 60 * 1000L
                updateTimerText()
            }
        }

        btnStartReset.setOnClickListener {
            if (isRunning) {
                showResetConfirmationDialog()
            } else {
                if (isTimerFinished) {
                    switchPhase()
                }
                startTimer()
                btnStartReset.text = "Сбросить"
            }
        }

        btnStopSound.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.prepare()
            }
            btnStopSound.visibility = View.GONE
        }

        // Обработчики для кнопок билетов
        binding.btnTicket1.setOnClickListener { handleDistractionTicket(1) }
        binding.btnTicket3.setOnClickListener { handleDistractionTicket(3) }
        binding.btnTicket5.setOnClickListener { handleDistractionTicket(5) }
        binding.btnContinue.setOnClickListener {
            endDistraction()
            // Автоматически запускаем таймер работы
            if (currentPhase == Phase.WORK) {
                startTimer()
            }
        }

        currentPhase = Phase.WORK
        timeLeftInMillis = binding.npWork.value * 60 * 1000L
        binding.phaseLabel.text = "Работа"
        updateTimerText()

        alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), TimerReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Скрываем элементы отвлечения изначально
        binding.tvDistractionTimer.visibility = View.GONE
        binding.btnContinue.visibility = View.GONE
    }

    private fun showResetConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Подтверждение сброса")
            .setMessage("Вы уверены? Сбросится ударный режим")
            .setPositiveButton("Сбросить") { _, _ ->
                resetTimer()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetTimer() {
        cancelAlarm()
        timer?.cancel()
        binding.btnStartReset.isEnabled = true
        distractionTimer?.cancel()
        remainingBreakTime = 0L
        binding.btnStopSound.visibility = View.GONE
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.prepare()
        }

        lifecycleScope.launch {
            try {
                val stats = timerRepository.getStats(userId)
                stats.currentStreak = 0
                timerRepository.updateStats(stats)
            } catch (e: Exception) {
                Log.e("Pomodoro", "Error resetting streak", e)
            }
        }

        binding.btnStopSound.visibility = View.GONE
        currentPhase = Phase.WORK
        timeLeftInMillis = binding.npWork.value * 60 * 1000L
        isRunning = false
        isTimerFinished = false
        binding.btnStartReset.text = "Старт"
        binding.phaseLabel.text = "Работа"
        updateTimerText()
        updatePickersState()
        distractionTicketsUsed = 0
        endDistraction()

        pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            Intent(requireContext(), TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startTimer() {
        cancelAlarm()
        binding.btnStopSound.visibility = View.GONE
        if (isDistractionActive) {
            binding.progressIndicator.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).withEndAction {
                binding.progressIndicator.animate().scaleX(1f).scaleY(1f).setDuration(200)
            }
        }

        val triggerTime = SystemClock.elapsedRealtime() + timeLeftInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canSchedule = (requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .canScheduleExactAlarms()

                if (canSchedule) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setWindow(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        60_000L,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("Pomodoro", "Alarm permission error", e)
        }

        val totalTime = when(currentPhase) {
            Phase.WORK -> binding.npWork.value * 60 * 1000L
            Phase.BREAK -> binding.npBreak.value * 60 * 1000L
        }

        mediaPlayer.stop()
        mediaPlayer.prepare()

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
            }

            override fun onFinish() {
                lifecycleScope.launch {
                    try {
                        scoreRepository.addPoints(userId, 10)
                        (activity as? MainActivity)?.updateScoreDisplay()

                        val stats = timerRepository.getStats(userId)
                        val actualFocusMinutes = (binding.npWork.value - (timeLeftInMillis / (60 * 1000))).toInt()
                        stats.totalFocusMinutes += actualFocusMinutes
                        stats.completedSessions++

                        stats.currentStreak++
                        if (stats.currentStreak > stats.maxStreak) {
                            stats.maxStreak = stats.currentStreak
                        }

                        stats.sessionsWith1Ticket += if (distractionTicketsUsed == 1) 1 else 0
                        stats.sessionsWith2Tickets += if (distractionTicketsUsed == 2) 1 else 0
                        stats.sessionsWith3PlusTickets += if (distractionTicketsUsed >= 3) 1 else 0
                        stats.perfectSessions += if (distractionTicketsUsed == 0) 1 else 0

                        timerRepository.updateStats(stats)
                        distractionTicketsUsed = 0
                    } catch (e: Exception) {
                        Log.e("Pomodoro", "Error updating stats", e)
                    }
                }

                isRunning = false
                isTimerFinished = true
                binding.btnStartReset.text = "Старт"
                binding.btnStopSound.visibility = View.VISIBLE // Показываем кнопку звука
                mediaPlayer.start()

                updatePickersState()

            }
        }.start()
        binding.btnStartReset.text = if (isRunning) "Сбросить" else "Старт"
        isRunning = true
        isTimerFinished = false
        btnStartReset.text = "Сбросить"
        updatePickersState()
    }

    private fun switchPhase() {
        currentPhase = if (currentPhase == Phase.WORK) Phase.BREAK else Phase.WORK
        timeLeftInMillis = when (currentPhase) {
            Phase.WORK -> binding.npWork.value * 60 * 1000L
            Phase.BREAK -> binding.npBreak.value * 60 * 1000L
        }
        binding.phaseLabel.text = if (currentPhase == Phase.WORK) "Работа" else "Отдых"
        updateTimerText()
        updatePickersState()
    }

    private fun updatePickersState() {
        // Кнопки активны только когда таймер работает и нет активного купона
        val ticketsEnabled = isRunning && !isDistractionActive

        // Кнопки видны всегда, кроме времени активного купона
        val ticketsVisible = !isDistractionActive

        binding.btnTicket1.isEnabled = ticketsEnabled
        binding.btnTicket3.isEnabled = ticketsEnabled
        binding.btnTicket5.isEnabled = ticketsEnabled

        binding.btnTicket1.visibility = if (ticketsVisible) View.VISIBLE else View.GONE
        binding.btnTicket3.visibility = if (ticketsVisible) View.VISIBLE else View.GONE
        binding.btnTicket5.visibility = if (ticketsVisible) View.VISIBLE else View.GONE
        binding.btnStartReset.alpha = if (binding.btnStartReset.isEnabled) 1f else 0.6f


        binding.tvTicketsLabel.visibility = View.VISIBLE
        binding.tvTicketsLabel.alpha = if (ticketsEnabled) 1f else 0.6f

        when {
            isRunning -> {
                binding.npWork.isEnabled = (currentPhase != Phase.WORK)
                binding.npBreak.isEnabled = (currentPhase != Phase.BREAK)
            }
            isDistractionActive -> {
                binding.npWork.isEnabled = false
                binding.npBreak.isEnabled = false
            }
            else -> {
                binding.npWork.isEnabled = true
                binding.npBreak.isEnabled = true
            }
        }

        binding.npWork.alpha = if (binding.npWork.isEnabled) 1f else 0.6f
        binding.npBreak.alpha = if (binding.npBreak.isEnabled) 1f else 0.6f
    }

    private fun handleDistractionTicket(minutes: Int) {

        binding.btnStartReset.isEnabled = false
        val cost = when (minutes) {
            1 -> 10
            3 -> 30
            5 -> 50
            else -> 0
        }

        // Получаем текущие очки пользователя
        val currentPoints = scoreRepository.getCurrentScore(userId)

        // Проверяем, достаточно ли очков для покупки
        if (currentPoints < cost) {
            // Показываем сообщение о недостатке очков
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Недостаточно очков")
                .setMessage("У вас недостаточно очков для покупки этого купона.")
                .setPositiveButton("ОК", null)
                .show()
            return
        }

        // Сохраняем текущее состояние
        if (currentPhase == Phase.BREAK) {
            remainingBreakTime = timeLeftInMillis
        }

        if (isRunning) {
            timer?.cancel()
            isRunning = false
        }

        // Уменьшаем очки
        lifecycleScope.launch {
            scoreRepository.updatePoints(userId, -cost)
            (activity as? MainActivity)?.updateScoreDisplay() // Обновляем отображение очков
        }

        distractionTicketsUsed += minutes
        distractionMinutes = minutes
        isDistractionActive = true

        binding.btnContinue.visibility = View.VISIBLE
        binding.tvDistractionTimer.visibility = View.VISIBLE

        distractionTimer = object : CountDownTimer(minutes * 60 * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvDistractionTimer.text = String.Companion.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    millisUntilFinished / 1000 / 60,
                    (millisUntilFinished / 1000) % 60)
            }

            override fun onFinish() {
                endDistraction()
            }
        }.start()

        updatePickersState()
    }



    private fun endDistraction() {
        distractionTimer?.cancel()
        binding.btnStartReset.isEnabled = true
        isDistractionActive = false

        binding.tvDistractionTimer.visibility = View.GONE
        binding.btnContinue.visibility = View.GONE


        if (remainingBreakTime > 0) {
            currentPhase = Phase.BREAK
            timeLeftInMillis = remainingBreakTime
            remainingBreakTime = 0L
            binding.phaseLabel.text = "Отдых"
            updateTimerText()
            startTimer() // Автоматически продолжаем
        }

        updatePickersState()
    }

    fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        binding.tvTimer.text = String.Companion.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        val totalTime = when(currentPhase) {
            Phase.WORK -> binding.npWork.value * 60 * 1000L
            Phase.BREAK -> binding.npBreak.value * 60 * 1000L
        }
        val progress = ((totalTime - timeLeftInMillis).toFloat() / totalTime * 100).toInt()
        progressIndicator.setProgressCompat(progress, true)
    }

    private fun cancelAlarm() {
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e("Pomodoro", "Error canceling alarm", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelAlarm()
        timer?.cancel()
        distractionTimer?.cancel()
        mediaPlayer.release()
        _binding = null

        lifecycleScope.launch {
            try {
                timerRepository.syncStats(userId)
            } catch (e: Exception) {
                Log.e("Pomodoro", "Error syncing stats", e)
            }
        }
    }
}