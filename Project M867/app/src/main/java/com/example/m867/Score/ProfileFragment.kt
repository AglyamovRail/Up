package com.example.m867.Score

import TimerStats
import android.R
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.m867.AuthActivity
import com.example.m867.Habit.HabitDbHelper
import com.example.m867.MainActivity
import com.example.m867.NetworkUtils
import com.example.m867.Task.TaskRepository
import com.example.m867.Timer.TimerRepository
import com.example.m867.databinding.FragmentProfileBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private enum class StatsType {
        TASKS, TIMER, HABITS
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!! // Геттер для безопасного доступа

    private lateinit var auth: FirebaseAuth
    private lateinit var scoreRepository: ScoreRepository
    private lateinit var dbHelper: HabitDbHelper


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initDependencies()
        setupUI()
        loadInitialData()
        scoreRepository = ScoreRepository(requireContext())
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        binding.tvName.text = "Имя: ${user?.displayName ?: "не указано"}"
        binding.tvEmail.text = "Почта: ${user?.email ?: "неизвестно"}"

        binding.btnGetMotivation.setOnClickListener {
            showMotivationDialog()
        }

        binding.btnGetPrediction.setOnClickListener {
            showPredictionDialog()
        }


        binding.tvTitle.text = "Очки: ${scoreRepository.getCurrentScore(user?.uid ?: "")}"



        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Выход")
                .setMessage("Вы уверены, что хотите выйти из аккаунта?")
                .setPositiveButton("Да") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(requireContext(), AuthActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }


        // Настройка спиннера выбора статистики
        val statsTypes = arrayOf("Задачи", "Таймер", "Привычки")
        val adapter = ArrayAdapter(requireContext(), R.layout.simple_spinner_item, statsTypes)
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatsType.adapter = adapter

        // Скрываем контейнеры при старте
        binding.containerStats.visibility = View.GONE
        binding.layoutStatsContainer.visibility = View.GONE

        // Обработчик выбора типа статистики
        binding.spinnerStatsType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Сначала скрываем все контейнеры
                binding.containerStats.visibility = View.GONE
                binding.layoutStatsContainer.visibility = View.GONE

                when (position) {
                    0 -> {
                        binding.containerStats.visibility = View.VISIBLE
                        showTaskStats()
                    }
                    1 -> {
                        binding.containerStats.visibility = View.VISIBLE
                        showTimerStats()
                    }
                    2 -> {
                        binding.layoutStatsContainer.visibility = View.VISIBLE
                        showHabitStats()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Общий обработчик обновления
        binding.swipeRefreshLayoutProfile.setOnRefreshListener {
            refreshAllData()
        }

        // Устанавливаем выбор по умолчанию и загружаем данные
        binding.spinnerStatsType.setSelection(0)
        binding.swipeRefreshLayoutProfile.isRefreshing = true
        binding.containerStats.visibility = View.VISIBLE
        showTaskStats()



        // Загрузка при старте
        binding.swipeRefreshLayoutProfile.isRefreshing = true
        loadHabitStats()


    }

    private fun initDependencies() {
        auth = FirebaseAuth.getInstance()
        scoreRepository = ScoreRepository(requireContext())
        dbHelper = HabitDbHelper(requireContext()) // Важная инициализация!
    }

    private fun setupUI() {
        binding.tvName.text = "Имя: ${auth.currentUser?.displayName ?: "не указано"}"
        binding.tvEmail.text = "Почта: ${auth.currentUser?.email ?: "неизвестно"}"

    }

    private fun loadInitialData() {
        binding.swipeRefreshLayoutProfile.isRefreshing = true
        refreshAllData()
    }

    private fun refreshTaskStats() {
        if (!isAdded || isDetached) {
            binding.swipeRefreshLayoutProfile.isRefreshing = false
            return
        }

        // Проверяем, есть ли уже созданный View с графиком
        val statsView = if (binding.containerStats.childCount > 0) {
            binding.containerStats.getChildAt(0)
        } else {
            layoutInflater.inflate(com.example.m867.R.layout.item_task_stat, binding.containerStats, false).also {
                binding.containerStats.addView(it)
            }
        }

        loadTaskStats(statsView)
    }


    private fun showMotivationDialog() {
        val userId = auth.currentUser?.uid ?: return
        val currentPoints = scoreRepository.getCurrentScore(userId)

        if (currentPoints < 30) {
            AlertDialog.Builder(requireContext())
                .setTitle("Недостаточно очков")
                .setMessage("Вам нужно 30 очков для получения мотивационной фразы. Выполняйте больше задач!")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Получить мотивацию")
            .setMessage("Получить случайную мотивационную цитату за 30 очков?")
            .setPositiveButton("Да") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Списываем очки
                        scoreRepository.updatePoints(userId, -30)

                        // Обновляем отображение очков
                        (activity as? MainActivity)?.updateScoreDisplay()

                        // Показываем цитату
                        showRandomQuote()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRandomQuote() {
        val quotes = resources.getStringArray(com.example.m867.R.array.motivational_quotes)
        val randomQuote = quotes.random()

        AlertDialog.Builder(requireContext())
            .setTitle("Мотивация")
            .setMessage(randomQuote)
            .setPositiveButton("Спасибо!", null)
            .show()
    }


    private fun showPredictionDialog() {
        val userId = auth.currentUser?.uid ?: return
        val currentPoints = scoreRepository.getCurrentScore(userId)

        if (currentPoints < 50) {
            showNotEnoughPointsDialog()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Получить предсказание")
            .setMessage("Хотите узнать, что вас ждёт? Всего 50 очков!")
            .setPositiveButton("Да, хочу!") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Списываем очки
                        scoreRepository.updatePoints(userId, -50)

                        // Обновляем отображение очков
                        (activity as? MainActivity)?.updateScoreDisplay()

                        // Показываем предсказание
                        showRandomPrediction()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Передумал", null)
            .show()
    }

    private fun showRandomPrediction() {
        val predictions = resources.getStringArray(com.example.m867.R.array.predictive_phrases)
        val randomPrediction = predictions.random()

        AlertDialog.Builder(requireContext())
            .setTitle("🔮 Ваше предсказание")
            .setMessage(randomPrediction)
            .setPositiveButton("Ого!", null)
            .show()
    }

    private fun showNotEnoughPointsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Недостаточно очков")
            .setMessage("Вам нужно 50 очков. Выполняйте задачи и привычки!")
            .setPositiveButton("Хорошо", null)
            .show()
    }


    private fun updateScoreDisplay() {
        val userId = auth.currentUser?.uid ?: return
        val points = scoreRepository.getCurrentScore(userId)
        binding.tvTitle.text = "Очки: $points"
    }

    fun updateScoreDisplay(points: Int) {
        if (isAdded && !isDetached && _binding != null) {
            binding.tvTitle.text = "Очки: $points"
        }
    }

    private fun refreshAllData() {
        // Обновляем отображение очков
        updateScoreDisplay()

        when (binding.spinnerStatsType.selectedItemPosition) {
            0 -> refreshTaskStats() // Задачи
            1 -> refreshTimerStats() // Таймер
            2 -> refreshHabitStats() // Привычки
        }
    }

    private fun refreshTimerStats() {
        // Очищаем контейнер и добавляем индикатор загрузки
        binding.containerStats.removeAllViews()
        val loadingView = layoutInflater.inflate(
            com.example.m867.R.layout.item_loading,
            binding.containerStats,
            false
        )
        binding.containerStats.addView(loadingView)

        lifecycleScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val timerRepo = TimerRepository(requireContext())

                // Синхронизируем данные с сервером перед загрузкой
                if (NetworkUtils.isNetworkAvailable(requireContext())) {
                    timerRepo.syncStats(userId)
                }

                val stats = timerRepo.getStats(userId)

                withContext(Dispatchers.Main) {
                    // Удаляем индикатор загрузки
                    binding.containerStats.removeView(loadingView)

                    // Добавляем статистику
                    val statsView = layoutInflater.inflate(
                        com.example.m867.R.layout.item_timer_stat,
                        binding.containerStats,
                        false
                    )
                    binding.containerStats.addView(statsView)

                    // Заполняем данные
                    statsView.findViewById<TextView>(com.example.m867.R.id.tvTotalFocus).text =
                        " ${stats.totalFocusMinutes} мин"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvCompletedSessions).text =
                        " ${stats.completedSessions}"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvCurrentStreak).text =
                        " ${stats.currentStreak}"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvMaxStreak).text =
                        " ${stats.maxStreak}"

                    setupTicketChart(statsView, stats)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Удаляем индикатор загрузки
                    binding.containerStats.removeView(loadingView)

                    // Показываем сообщение об ошибке
                    val errorView = layoutInflater.inflate(
                        com.example.m867.R.layout.empty_stat_view,
                        binding.containerStats,
                        false
                    ).apply {
                        findViewById<TextView>(com.example.m867.R.id.tvEmptyMessage).text =
                            "Ошибка загрузки статистики"
                    }
                    binding.containerStats.addView(errorView)
                    Log.e("ProfileFragment", "Timer stats load error", e)
                }
            } finally {
                binding.swipeRefreshLayoutProfile.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun showTaskStats() {
        binding.containerStats.removeAllViews()
        val statsView = layoutInflater.inflate(com.example.m867.R.layout.item_task_stat, binding.containerStats, false)
        binding.containerStats.addView(statsView)

        loadTaskStats(statsView)
    }

    private fun loadTaskStats(statsView: View) {
        binding.containerStats.removeAllViews()

        // Показываем индикатор загрузки
        val loadingView = layoutInflater.inflate(
            com.example.m867.R.layout.item_loading,
            binding.containerStats,
            false
        )
        binding.containerStats.addView(loadingView)

        lifecycleScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch

                // Получаем задачи для статистики
                val tasks = withContext(Dispatchers.IO) {
                    TaskRepository(requireContext())
                        .getCompletedTasksForStats(userId)
                }

                // Группируем по дате выполнения
                val taskCountByDate = tasks
                    .groupBy { task ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(task.completedAt!!.toDate())
                    }
                    .mapValues { it.value.size }
                    .toSortedMap()

                withContext(Dispatchers.Main) {
                    binding.containerStats.removeView(loadingView)

                    if (taskCountByDate.isEmpty()) {
                        // Показываем заглушку если нет данных
                        val emptyView = layoutInflater.inflate(
                            com.example.m867.R.layout.empty_stat_view,
                            binding.containerStats,
                            false
                        ).apply {
                            findViewById<TextView>(com.example.m867.R.id.tvEmptyMessage).text =
                                "Нет данных о выполненных задачах"
                        }
                        binding.containerStats.addView(emptyView)
                    } else {
                        // Показываем график
                        setupTaskChart(statsView, taskCountByDate)
                        binding.containerStats.addView(statsView)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.containerStats.removeView(loadingView)

                    // Показываем ошибку
                    val errorView = layoutInflater.inflate(
                        com.example.m867.R.layout.empty_stat_view,
                        binding.containerStats,
                        false
                    ).apply {
                        findViewById<TextView>(com.example.m867.R.id.tvEmptyMessage).text =
                            "Ошибка загрузки статистики"
                    }
                    binding.containerStats.addView(errorView)
                    Log.e("ProfileFragment", "Stats load error", e)
                }
            } finally {
                binding.swipeRefreshLayoutProfile.isRefreshing = false
            }
        }
    }


    private fun setupTaskChart(statsView: View, data: Map<String, Int>) {
        val chart = statsView.findViewById<LineChart>(com.example.m867.R.id.chartTasks)
        val noDataText = statsView.findViewById<TextView>(com.example.m867.R.id.tvNoData)
        chart.clear()

        if (data.isEmpty()) {
            chart.visibility = View.GONE
            noDataText.visibility = View.VISIBLE
            noDataText.text = "Нет данных для отображения"
            return
        }

        chart.visibility = View.VISIBLE
        noDataText.visibility = View.GONE

        val dates = data.keys.toList()
        val colorPrimary = ContextCompat.getColor(requireContext(), com.example.m867.R.color.turquoise)
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // Создаем entries с целыми числами
        val entries = data.entries.mapIndexed { index, entry ->
            Entry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "Выполненные задачи").apply {
            color = colorPrimary
            lineWidth = 2.5f
            setCircleColor(colorPrimary)
            circleRadius = 4f
            valueTextSize = 14f
            valueTextColor = if (isDarkTheme) Color.WHITE else Color.BLACK
            setDrawValues(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER

            // Форматирование значений точек (целые числа)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString() // Округляем до целого
                }
            }
        }

        chart.apply {
            this.data = LineData(dataSet)
            setDrawMarkers(false)

            // Настройка оси X
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = minOf(7, dates.size)
                textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return dates.getOrNull(value.toInt())?.substring(5) ?: ""
                    }
                }
            }

            // Настройка левой оси Y
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = if (isDarkTheme) Color.DKGRAY else Color.LTGRAY
                axisMinimum = 0f
                granularity = 1f // Шаг 1
                textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
                textSize = 12f

                // Форматирование значений оси Y
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString() // Округляем до целого
                    }
                }
            }

            // Отключаем правую ось
            axisRight.isEnabled = false

            // Общие настройки
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setNoDataText("Нет данных для отображения")
            setNoDataTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            setDrawBorders(false)

            // Анимация
            animateX(1000)
            invalidate()
        }
    }

    private fun showTimerStats() {
        binding.containerStats.removeAllViews()
        binding.containerStats.visibility = View.VISIBLE
        binding.swipeRefreshLayoutProfile.isRefreshing = true
        val statsView = layoutInflater.inflate(com.example.m867.R.layout.item_timer_stat, binding.containerStats, false)
        binding.containerStats.addView(statsView)

        lifecycleScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val timerRepo = TimerRepository(requireContext())
                val stats = timerRepo.getStats(userId)

                withContext(Dispatchers.Main) {
                    statsView.findViewById<TextView>(com.example.m867.R.id.tvTotalFocus).text =
                        "Общее время: ${stats.totalFocusMinutes} мин"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvCompletedSessions).text =
                        "Завершено циклов: ${stats.completedSessions}"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvCurrentStreak).text =
                        "Текущая серия: ${stats.currentStreak}"

                    statsView.findViewById<TextView>(com.example.m867.R.id.tvMaxStreak).text =
                        "Макс. серия: ${stats.maxStreak}"

                    setupTicketChart(statsView, stats)
                }
            } catch (e: Exception) {
                Log.e("Profile", "Error loading timer stats", e)
            }
        }
        refreshTimerStats()
    }

    private fun setupTicketChart(statsView: View, stats: TimerStats) {
        val chart = statsView.findViewById<PieChart>(com.example.m867.R.id.chartTickets)
        chart.clear()


        // Создаем список только с ненулевыми значениями
        val entries = mutableListOf<PieEntry>().apply {
            if (stats.perfectSessions > 0) add(PieEntry(stats.perfectSessions.toFloat()))
            if (stats.sessionsWith1Ticket > 0) add(PieEntry(stats.sessionsWith1Ticket.toFloat()))
            if (stats.sessionsWith2Tickets > 0) add(PieEntry(stats.sessionsWith2Tickets.toFloat()))
            if (stats.sessionsWith3PlusTickets > 0) add(PieEntry(stats.sessionsWith3PlusTickets.toFloat()))
        }

        // Если нет данных - скрываем диаграмму
        if (entries.isEmpty()) {
            chart.visibility = View.GONE
            statsView.findViewById<TextView>(com.example.m867.R.id.tvNoData).visibility = View.VISIBLE
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            // Цвета для сегментов
            colors = listOf(
                ContextCompat.getColor(requireContext(), com.example.m867.R.color.green),
                ContextCompat.getColor(requireContext(), com.example.m867.R.color.my_yellow),
                ContextCompat.getColor(requireContext(), com.example.m867.R.color.orange),
                ContextCompat.getColor(requireContext(), com.example.m867.R.color.red)
            )

            // Настройки отображения значений
            setDrawValues(true)
            valueTextSize = 20f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            sliceSpace = 2f
            selectionShift = 5f
        }

        chart.apply {
            data = PieData(dataSet)

            // Полностью отключаем стандартную легенду
            legend.isEnabled = false

            // Создаем свою легенду вручную
            val customLegend = layoutInflater.inflate(
                com.example.m867.R.layout.custom_pie_legend,
                statsView as ViewGroup,
                false
            )

            // Настраиваем элементы легенды
            customLegend.findViewById<TextView>(com.example.m867.R.id.legendPerfect).apply {
                text = "Без купонов"
                setCompoundDrawablesWithIntrinsicBounds(
                    createCircleDrawable(ContextCompat.getColor(context, com.example.m867.R.color.green)),
                    null, null, null
                )
            }

            customLegend.findViewById<TextView>(com.example.m867.R.id.legend1Coupon).apply {
                text = "1 купон"
                setCompoundDrawablesWithIntrinsicBounds(
                    createCircleDrawable(ContextCompat.getColor(context, com.example.m867.R.color.my_yellow)),
                    null, null, null
                )
            }

            customLegend.findViewById<TextView>(com.example.m867.R.id.legend2Coupons).apply {
                text = "2 купона"
                setCompoundDrawablesWithIntrinsicBounds(
                    createCircleDrawable(ContextCompat.getColor(context, com.example.m867.R.color.orange)),
                    null, null, null
                )
            }

            customLegend.findViewById<TextView>(com.example.m867.R.id.legend3PlusCoupons).apply {
                text = "3+ купона"
                setCompoundDrawablesWithIntrinsicBounds(
                    createCircleDrawable(ContextCompat.getColor(context, com.example.m867.R.color.red)),
                    null, null, null
                )
            }

            (statsView as ViewGroup).addView(customLegend)

            // Общие настройки диаграммы
            description.isEnabled = false
            setEntryLabelColor(Color.TRANSPARENT)
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            setDrawEntryLabels(false)
            setUsePercentValues(false)
            rotationAngle = 0f
            isRotationEnabled = false
            setTouchEnabled(false)

            animateY(1000)
            invalidate()
        }
    }

    private fun createCircleDrawable(color: Int): Drawable {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(color)
        shape.setSize(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
        )
        return shape
    }

    private fun isDarkTheme(): Boolean {
        return resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun showHabitStats() {
        binding.layoutStatsContainer.removeAllViews()
        loadHabitStats()
    }

    private fun Date.toLocalDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(this)
    }

    private fun loadHabitStats() {
        if (!isAdded || isDetached) {
            binding.swipeRefreshLayoutProfile.isRefreshing = false
            return
        }

        val statsContainer = binding.layoutStatsContainer
        statsContainer.removeAllViews()

        // Добавляем индикатор загрузки
        val loadingView = layoutInflater.inflate(
            com.example.m867.R.layout.item_loading,
            statsContainer,
            false
        )
        statsContainer.addView(loadingView)

        val uid = auth.currentUser?.uid ?: run {
            binding.swipeRefreshLayoutProfile.isRefreshing = false
            statsContainer.removeView(loadingView)
            return
        }

        lifecycleScope.launch {
            try {
                // Получаем все привычки пользователя
                val habits = withContext(Dispatchers.IO) {
                    dbHelper.getHabits(uid)
                }

                if (habits.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        statsContainer.removeView(loadingView)
                        val emptyView = layoutInflater.inflate(
                            com.example.m867.R.layout.empty_stat_view,
                            statsContainer,
                            false
                        ).apply {
                            findViewById<TextView>(com.example.m867.R.id.tvEmptyMessage).text =
                                "У вас пока нет привычек"
                        }
                        statsContainer.addView(emptyView)
                    }
                    return@launch
                }

                // Для каждой привычки считаем статистику
                habits.forEach { habit ->
                    // Получаем все записи для привычки
                    val records = withContext(Dispatchers.IO) {
                        dbHelper.getHabitRecordsForHabit(habit.id)
                    }

                    // Фильтруем только выполненные записи
                    val completedRecords = records.filter { it.isCompleted }
                    val hasAnyCompletions = completedRecords.isNotEmpty()

                    // Получаем ожидаемое количество выполнений только если есть хотя бы одно выполнение
                    val expectedCompletions = if (hasAnyCompletions) {
                        withContext(Dispatchers.IO) {
                            dbHelper.getExpectedCompletions(habit.id)
                        }.coerceAtLeast(1)
                    } else {
                        0
                    }

                    val actualCompletions = completedRecords.size

                    // Рассчитываем процент выполнения только если есть выполнения
                    val percent = if (hasAnyCompletions && expectedCompletions > 0) {
                        (actualCompletions.toFloat() / expectedCompletions * 100).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                    // Создаем view для статистики
                    withContext(Dispatchers.Main) {
                        val statView = layoutInflater.inflate(
                            com.example.m867.R.layout.item_habit_stat,
                            statsContainer,
                            false
                        ).apply {
                            findViewById<TextView>(com.example.m867.R.id.tvStatHabitName).text =
                                habit.name

                            // Скрываем процент, если нет выполнений
                            val percentView =
                                findViewById<TextView>(com.example.m867.R.id.tvPercent)
                            percentView.text = if (hasAnyCompletions) "$percent%" else ""
                            percentView.visibility =
                                if (hasAnyCompletions) View.VISIBLE else View.GONE

                            // Комментарий для новых привычек
                            findViewById<TextView>(com.example.m867.R.id.tvComment).text =
                                if (hasAnyCompletions) {
                                    when {
                                        percent <= 50 -> "Можно лучше!"
                                        percent < 80 -> "Хороший результат"
                                        else -> "Отличная дисциплина!"
                                    }
                                } else {
                                    "Ещё не начато"
                                }

                            // Показываем статистику выполнений только если есть хотя бы одно выполнение
                            val doneCountView =
                                findViewById<TextView>(com.example.m867.R.id.tvDoneCount)
                            if (hasAnyCompletions) {
                                doneCountView.text =
                                    "Выполнено: $actualCompletions из $expectedCompletions"
                                doneCountView.visibility = View.VISIBLE
                            } else {
                                doneCountView.visibility = View.GONE
                            }

                            // Добавляем информацию о периодичности
                            val periodText = when (habit.periodType) {
                                1 -> " (через день)"
                                2 -> " (каждые ${habit.periodDays} дня)"
                                else -> " (ежедневно)"
                            }
                            findViewById<TextView>(com.example.m867.R.id.tvPeriodInfo).text =
                                periodText
                        }

                        statsContainer.addView(statView)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading habit stats", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка загрузки статистики",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    statsContainer.removeView(loadingView)
                    binding.swipeRefreshLayoutProfile.isRefreshing = false
                }
            }
        }
    }

    fun refreshHabitStats() {
        if (isVisible) { // Проверяем, что фрагмент видим
            binding.swipeRefreshLayoutProfile.isRefreshing = true
            loadHabitStats()
        }
    }
}