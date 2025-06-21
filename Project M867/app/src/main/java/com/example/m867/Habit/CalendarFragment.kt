package com.example.m867.Habit

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.m867.AuthActivity
import com.example.m867.DayAdapter
import com.example.m867.MainActivity
import com.example.m867.R
import com.example.m867.Score.ScoreRepository
import com.example.m867.databinding.FragmentCalendarBinding
import com.google.android.flexbox.FlexboxLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class CalendarFragment : Fragment() {

    // 1. Инициализация auth
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var binding: FragmentCalendarBinding
    private lateinit var dayAdapter: DayAdapter
    private val daysList = mutableListOf<Date>()
    private lateinit var dbHelper: HabitDbHelper
    private lateinit var repo: HabitRepository
    private lateinit var scoreRepository: ScoreRepository
    private val habits = mutableListOf<Habit>()
    private var lastSyncedMonth: Int = -1

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, b: Bundle?) =
        FragmentCalendarBinding.inflate(inflater, c, false).also { binding = it }.root

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Проверка авторизации
        val userId = auth.currentUser?.uid ?: run {
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
            return
        }

        // 3. Инициализация зависимостей ПОСЛЕ проверки auth
        scoreRepository = ScoreRepository(requireContext())
        dbHelper = HabitDbHelper(requireContext())
        repo = HabitRepository(requireContext())
        lastSyncedMonth = Calendar.getInstance().get(Calendar.MONTH)

        // 4. Убрали дублирующую инициализацию auth
        generateDays()

        // 5. Инициализация адаптера
        dayAdapter = DayAdapter(daysList).apply {
            onHabitToggled = { habitId, dateStr, isDone ->
                lifecycleScope.launch {
                    // 1. Обновляем данные
                    val record = HabitRecord(
                        id = UUID.randomUUID().toString(),
                        habitId = habitId,
                        date = dateStr,
                        isCompleted = isDone,
                        userId = userId
                    )

                    try {
                        // 2. Выполняем операцию с базой
                        if (isDone) {
                            repo.addRecord(record)
                        } else {
                            repo.deleteRecord(record)
                        }

                        // 3. Обновляем UI после завершения операции
                        withContext(Dispatchers.Main) {
                            // Обновляем записи
                            reloadRecords()
                            // Принудительно обновляем адаптер
                            dayAdapter.notifyDataSetChanged()
                        }

                        // 4. Обновляем очки
                        val pointsDelta = if (isDone) 5 else -5
                        scoreRepository.updatePoints(userId, pointsDelta)
                        (requireActivity() as MainActivity).updateScoreDisplay()

                    } catch (e: Exception) {
                        Log.e("HabitToggle", "Error: ${e.message}")
                    }
                }
            }
        }
        binding.fabAddHabit.setOnLongClickListener {
            // 1. Создаем дату 1 июня 2025
            val newDate = Calendar.getInstance().apply {
                set(2025, Calendar.JUNE, 1, 0, 0, 0)
            }.time

            // 2. ID привычки
            val habitId = "4d026f04-db69-49d8-be9d-4f1903470f85"

            // 3. Обновляем дату
            dbHelper.updateHabitDate(habitId, newDate)

            // 4. Проверяем в логах
            val cursor = dbHelper.readableDatabase.query(
                "habits",
                arrayOf("createdAt"),
                "id = ?",
                arrayOf(habitId),
                null, null, null
            )

            cursor.moveToFirst()
            val timestamp = cursor.getLong(0)
            cursor.close()

            Log.d("DEBUG", "Новая дата: ${SimpleDateFormat("dd.MM.yyyy").format(Date(timestamp * 1000))}")

            // 5. Обновляем интерфейс
            loadHabits(auth.currentUser?.uid ?: "")

            true
        }
        binding.recyclerViewDays.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewDays.adapter = dayAdapter

        binding.swipeRefreshLayout.setDistanceToTriggerSync(300) // Увеличиваем минимальное расстояние для срабатывания
        binding.swipeRefreshLayout.isEnabled = false // По умолчанию отключаем


        binding.recyclerViewDays.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // При начале касания отключаем SwipeRefreshLayout
                    binding.swipeRefreshLayout.isEnabled = false
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // После завершения касания снова включаем SwipeRefreshLayout
                    binding.swipeRefreshLayout.isEnabled = !binding.recyclerViewDays.canScrollVertically(-1)
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        LinearSnapHelper().attachToRecyclerView(binding.recyclerViewDays)

        // Правильный обработчик скролла для SwipeRefreshLayout
        binding.recyclerViewDays.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        binding.swipeRefreshLayout.isEnabled = false
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val center = lm.findFirstVisibleItemPosition()
                        updateMonthYearTitle(daysList[center])
                        reloadRecords()

                        // Включаем SwipeRefreshLayout только если мы в самом верху
                        binding.swipeRefreshLayout.isEnabled = !recyclerView.canScrollVertically(-1)
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Определяем направление скролла
                if (abs(dx) > abs(dy)) {
                    // Горизонтальный скролл - полностью отключаем SwipeRefreshLayout
                    binding.swipeRefreshLayout.isEnabled = false
                }
            }
        })


        binding.fabAddHabit.setOnClickListener { showAddHabitDialog(userId) }

        // Swipe-to-sync
        binding.swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    repo.syncHabits(userId)
                    repo.syncRecords(userId)
                    loadHabits(userId)
                    Toast.makeText(requireContext(), "Данные синхронизированы", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка синхронизации: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("SYNC", "Ошибка синхронизации", e)
                } finally {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        // Инициализация
        updateMonthYearTitle(Date())
        binding.recyclerViewDays.scrollToPosition(daysList.size - 1)
        loadHabits(userId)
    }

    private fun generateDays() {
        val cal = Calendar.getInstance()
        val today = cal.time
        cal.add(Calendar.YEAR, -5)
        while (cal.time <= today) {
            daysList += cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun reloadRecords() {
        val recs = habits.flatMap { dbHelper.getHabitRecordsForHabit(it.id) }
        dayAdapter.updateRecords(recs)
    }

    private fun updateMonthYearTitle(date: Date) {
        binding.tvMonthYear.text =
            SimpleDateFormat("LLLL yyyy", Locale("ru")).format(date).replaceFirstChar { it.uppercase() }
    }

    private fun showAddHabitDialog(userId: String) {
        val dv = layoutInflater.inflate(R.layout.dialog_add_habit, null)
        val etName = dv.findViewById<EditText>(R.id.etHabitName)
        val etDesc = dv.findViewById<EditText>(R.id.etHabitDescription)
        val colorContainer = dv.findViewById<FlexboxLayout>(R.id.colorContainer)
        val radioGroup = dv.findViewById<RadioGroup>(R.id.radioGroupPeriod)
        val etDaysCount = dv.findViewById<EditText>(R.id.etDaysCount)
        val radioCustom = dv.findViewById<RadioButton>(R.id.radioCustom)
        val customDaysContainer = dv.findViewById<LinearLayout>(R.id.customDaysContainer)
        setupPeriodicityRadioGroup(radioGroup, etDaysCount, 0, 1) // По умолчанию "Каждый день"
        // Обработчик изменения выбора периодичности
        radioGroup.check(R.id.radioDaily)
        customDaysContainer.visibility = View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioCustom -> {
                    customDaysContainer.visibility = View.VISIBLE
                    etDaysCount.requestFocus()
                }
                else -> customDaysContainer.visibility = View.GONE
            }
        }

        etDaysCount.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(2))
        etDaysCount.keyListener = DigitsKeyListener.getInstance("123456789")

        val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#2196F3", "#4CAF50", "#FF9800", "#9E9E9E",
            "#3F51B5", "#8BC34A", "#CDDC39", "#FFC107", "#FF5722", "#795548", "#673AB7"
        )
        var selColor = colors[0]

        colors.forEach { c ->
            val v = View(requireContext()).apply {
                val sz = resources.getDimensionPixelSize(R.dimen.color_circle_size)
                layoutParams = FlexboxLayout.LayoutParams(sz, sz).apply {
                    setMargins(8, 8, 8, 8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(c))
                }
                setOnClickListener {
                    selColor = c
                    // обводка выбранного
                    for (i in 0 until colorContainer.childCount) {
                        (colorContainer.getChildAt(i).background as GradientDrawable).apply {
                            if (colors[i] == selColor) setStroke(4, Color.BLACK)
                            else                    setStroke(0, Color.TRANSPARENT)
                        }
                    }
                }
            }
            colorContainer.addView(v)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить привычку")
            .setView(dv)
            .setPositiveButton("Добавить") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isNotEmpty()) lifecycleScope.launch {
                    // Определяем периодичность
                    val periodType = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioEveryOtherDay -> 1
                        R.id.radioCustom -> 2
                        else -> 0
                    }

                    val periodDays = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioEveryOtherDay -> 2
                        R.id.radioCustom -> etDaysCount.text.toString().toIntOrNull() ?: 1
                        else -> 1
                    }

                    val h = Habit(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        description = desc,
                        colorHex = selColor,
                        userId = userId,
                        periodType = periodType,
                        periodDays = periodDays,
                        createdAt = Timestamp.Companion.now(),
                    )
                    repo.addHabit(h)
                    loadHabits(userId)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadHabits(userId: String) {
        habits.clear()
        habits += dbHelper.getHabits(userId)
        dayAdapter.updateHabits(habits)
        reloadRecords()
        val container = binding.layoutHabits
        container.removeAllViews()

        habits.forEach { h ->
            val iv = layoutInflater.inflate(R.layout.item_habit, container, false)
            iv.findViewById<TextView>(R.id.tvHabitName).text = h.name
            iv.findViewById<View>(R.id.viewColor).background.setTint(Color.parseColor(h.colorHex))

            // Добавляем обработчик для кнопки удаления
            iv.findViewById<ImageButton>(R.id.btnDeleteHabit).setOnClickListener {
                showDeleteConfirmationDialog(h, userId)
            }

            val tvDescription = iv.findViewById<TextView>(R.id.tvHabitDescription)
            tvDescription.text = h.description ?: ""
            tvDescription.visibility = if (h.description.isNullOrEmpty()) View.GONE else View.VISIBLE

            val streak = dbHelper.getStreakForHabit(h.id)
            val streakIndicator = iv.findViewById<View>(R.id.tvStreakIndicator)
            val streakCount = iv.findViewById<TextView>(R.id.tvStreakCount)
            val btnRestore = iv.findViewById<Button>(R.id.btnRestoreStreak)

            streakCount.text = streak.toString()
            streakIndicator.visibility = if (streak > 0) View.VISIBLE else View.INVISIBLE

            val (canRestore, missedDate) = dbHelper.canRestoreStreak(h.id)
            btnRestore.visibility = if (canRestore) View.VISIBLE else View.GONE

            btnRestore.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Восстановить ударный режим")
                    .setMessage("Восстановить пропущенный день ($missedDate)? Это потребует 10 очков.")
                    .setPositiveButton("Да") { _, _ ->
                        lifecycleScope.launch {
                            if (scoreRepository.getCurrentScore(userId) >= 10) {
                                restoreStreak(h.id, userId, missedDate!!)
                                loadHabits(userId)
                                reloadRecords()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Недостаточно очков!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            iv.setOnClickListener { showEditHabitDialog(h, userId) }
            val div = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.divider_height)
                )
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            container.addView(iv)
            container.addView(div)
        }
    }

    // Добавляем новый метод для подтверждения удаления
    private fun showDeleteConfirmationDialog(habit: Habit, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить привычку")
            .setMessage("Вы уверены, что хотите удалить привычку \"${habit.name}\"? Все связанные данные будут потеряны.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        repo.deleteHabit(habit.id)
                        loadHabits(userId)
                        Toast.makeText(requireContext(), "Привычка удалена", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка при удалении", Toast.LENGTH_SHORT).show()
                        Log.e("DELETE", "Ошибка удаления привычки", e)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun restoreStreak(habitId: String, userId: String, missedDate: String) {
        lifecycleScope.launch {
            // Включаем временный режим восстановления
            dayAdapter.setAllowPastDays(true)

            try {
                // Создаем запись для пропущенного дня
                val record = HabitRecord(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    date = missedDate,
                    isCompleted = true,
                    userId = userId
                )

                repo.addRecord(record)
                scoreRepository.updatePoints(userId, -10)

                // Обновляем данные
                loadHabits(userId)
                reloadRecords()

                Toast.makeText(
                    requireContext(),
                    "Ударный режим восстановлен!",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                // Выключаем режим восстановления
                dayAdapter.setAllowPastDays(false)
            }
        }
    }

    // Общая функция для настройки RadioGroup
    private fun setupPeriodicityRadioGroup(
        radioGroup: RadioGroup,
        etDaysCount: EditText,
        initialPeriodType: Int,
        initialPeriodDays: Int
    ) {
        // Устанавливаем текущее значение
        when (initialPeriodType) {
            1 -> radioGroup.check(R.id.radioEveryOtherDay)
            2 -> {
                radioGroup.check(R.id.radioCustom)
                etDaysCount.setText(initialPeriodDays.toString())
                etDaysCount.isEnabled = true
            }
            else -> radioGroup.check(R.id.radioDaily)
        }

        // Обработчик изменений
        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.radioCustom -> {
                    etDaysCount.isEnabled = true
                    etDaysCount.requestFocus()
                }
                else -> etDaysCount.isEnabled = false
            }
        }

        // Разрешаем ввод только цифр
        etDaysCount.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(2))
        etDaysCount.keyListener = DigitsKeyListener.getInstance("123456789")
    }
    private fun showEditHabitDialog(habit: Habit, userId: String) {
        val dv = layoutInflater.inflate(R.layout.dialog_edit_habit, null)
        val etName = dv.findViewById<EditText>(R.id.etHabitName)
        val etDesc = dv.findViewById<EditText>(R.id.etHabitDescription)
        val colorContainer = dv.findViewById<FlexboxLayout>(R.id.colorContainer)
        val radioGroup = dv.findViewById<RadioGroup>(R.id.radioGroupPeriod)
        val etDaysCount = dv.findViewById<EditText>(R.id.etDaysCount)
        val radioCustom = dv.findViewById<RadioButton>(R.id.radioCustom)
        val customDaysContainer = dv.findViewById<LinearLayout>(R.id.customDaysContainer)
        radioGroup.check(R.id.radioDaily)
        customDaysContainer.visibility = View.GONE
        // Заполняем текущие значения
        etName.setText(habit.name)
        etDesc.setText(habit.description)

        // Устанавливаем периодичность
        when {
            habit.periodType == 1 -> radioGroup.check(R.id.radioEveryOtherDay)
            habit.periodType == 2 -> {
                radioGroup.check(R.id.radioCustom)
                customDaysContainer.visibility = View.VISIBLE
                etDaysCount.setText(habit.periodDays.toString())
            }
            else -> radioGroup.check(R.id.radioDaily)
        }

        // Обработчик изменений
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioCustom -> {
                    customDaysContainer.visibility = View.VISIBLE
                    etDaysCount.requestFocus()
                }
                else -> customDaysContainer.visibility = View.GONE
            }
        }


        etDaysCount.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(2))
        etDaysCount.keyListener = DigitsKeyListener.getInstance("123456789")

        val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#2196F3", "#4CAF50", "#FF9800", "#9E9E9E",
            "#3F51B5", "#8BC34A", "#CDDC39", "#FFC107", "#FF5722", "#795548", "#673AB7"
        )
        var selColor = habit.colorHex

        colors.forEach { c ->
            val v = View(requireContext()).apply {
                val sz = resources.getDimensionPixelSize(R.dimen.color_circle_size)
                layoutParams = FlexboxLayout.LayoutParams(sz, sz).apply {
                    setMargins(8, 8, 8, 8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(c))
                    if (c == selColor) setStroke(4, Color.BLACK)
                }
                setOnClickListener {
                    selColor = c
                    for (i in 0 until colorContainer.childCount) {
                        (colorContainer.getChildAt(i).background as GradientDrawable).apply {
                            if (colors[i] == selColor) setStroke(4, Color.BLACK)
                            else                    setStroke(0, Color.TRANSPARENT)
                        }
                    }
                }
            }
            colorContainer.addView(v)
        }


        AlertDialog.Builder(requireContext())
            .setTitle("Редактировать привычку")
            .setView(dv)
            .setPositiveButton("Сохранить") { _, _ ->
                val updated = habit.copy(
                    name = etName.text.toString().trim(),
                    description = etDesc.text.toString().trim(),
                    colorHex = selColor,
                    periodType = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioEveryOtherDay -> 1
                        R.id.radioCustom -> 2
                        else -> 0
                    },
                    periodDays = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioCustom -> etDaysCount.text.toString().toIntOrNull() ?: 1
                        R.id.radioEveryOtherDay -> 2
                        else -> 1
                    }
                )
                lifecycleScope.launch {
                    repo.updateHabit(updated)
                    loadHabits(userId)
                    (requireActivity() as MainActivity).updateHabitStats()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}