package com.example.m867.Task


import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Paint
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.m867.AuthActivity
import com.example.m867.MainActivity
import com.example.m867.R
import com.example.m867.Score.ScoreRepository
import com.example.m867.databinding.FragmentTaskBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskFragment : Fragment() {

    private lateinit var binding: FragmentTaskBinding
    private lateinit var taskRepository: TaskRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var scoreRepository: ScoreRepository
    private lateinit var userId: String
    private lateinit var vibrator: Vibrator
    private var isArchiveExpanded = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTaskBinding.inflate(inflater, container, false)
        vibrator = requireActivity().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        taskRepository = TaskRepository(requireContext())
        scoreRepository = ScoreRepository(requireContext())
        userId = auth.currentUser?.uid ?: ""

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showLoginScreen()
            return
        }

        setupViews(currentUser.uid)
    }

    private fun setupViews(userId: String) {
        binding.swipeRefreshLayout.isEnabled = true
        binding.swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    taskRepository.syncTasks(userId)
                    loadTasks(userId)
                    Toast.makeText(requireContext(), "Синхронизировано", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка синхронизации", Toast.LENGTH_SHORT).show()
                    Log.e("TaskFragment", "Sync error", e)
                } finally {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        binding.spinnerSort.setSelection(1)
        binding.btnToggleArchive.setOnClickListener { toggleArchiveVisibility() }
        binding.fabAddTask.setOnClickListener { showAddTaskDialog(userId) }

        loadTasks(userId)

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                loadTasks(auth.currentUser?.uid ?: "")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnDeleteAllArchivedTasks.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удалить все архивные задачи")
                .setMessage("Вы уверены, что хотите удалить все задачи из архива?")
                .setPositiveButton("Удалить") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val userId = auth.currentUser?.uid ?: return@launch
                            val allTasks = taskRepository.getTasks(userId)
                            val archivedTasks = allTasks.filter { it.isCompleted }

                            archivedTasks.forEach { task ->
                                taskRepository.deleteTask(task.id)
                            }

                            loadTasks(userId)
                            Toast.makeText(requireContext(), "Архив очищен", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Ошибка при удалении архива", Toast.LENGTH_SHORT).show()
                            Log.e("TaskFragment", "Ошибка удаления архива", e)
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun loadTasks(userId: String) {
        lifecycleScope.launch {
            binding.swipeRefreshLayout.isRefreshing = true
            try {
                val tasks = when (binding.spinnerSort.selectedItemPosition) {
                    0 -> taskRepository.getTasksSortedByDateAsc(userId)
                    1 -> taskRepository.getTasks(userId)
                    2 -> taskRepository.getTasksSortedByPriority(userId)
                    else -> taskRepository.getTasks(userId)
                }
                displayTasks(tasks)
            } catch (e: Exception) {
                Log.e("TaskFragment", "Error loading tasks", e)
                Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun displayTasks(tasks: List<Task>) {
        // Очищаем списки без анимации
        binding.layoutTaskList.removeAllViews()

        // Очищаем архив только если он видим
        if (isArchiveExpanded) {
            binding.layoutArchiveList.removeAllViews()
        }

        val activeTasks = tasks.filter { !it.isCompleted }
        val archivedTasks = tasks.filter { it.isCompleted }

        // Добавляем активные задачи
        activeTasks.forEach { task ->
            binding.layoutTaskList.addView(createTaskView(task, false))
        }

        // Добавляем архивные задачи (если архив открыт)
        if (isArchiveExpanded) {
            archivedTasks.forEach { task ->
                binding.layoutArchiveList.addView(createTaskView(task, true))
            }
        }

        // Обновляем элементы управления архивом
        updateArchiveControls(archivedTasks.isNotEmpty())
    }

    private fun createTaskView(task: Task, isArchived: Boolean): View {
        val taskView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_task, null, false)

        val tvTask = taskView.findViewById<TextView>(R.id.tvTask)
        val cbCompleted = taskView.findViewById<CheckBox>(R.id.cbTaskCompleted)
        val btnDelete = taskView.findViewById<ImageButton>(R.id.btnDeleteTask)

        // Инициализация UI
        btnDelete.visibility = if (isArchived) View.VISIBLE else View.GONE
        tvTask.text = task.title
        cbCompleted.isChecked = task.isCompleted

        // Настройка стилей
        when (task.priority) {
            0 -> {
                cbCompleted.buttonTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.priority_high)
                )
                tvTask.setTextColor(getDefaultTextColor())
            }
            1 -> {
                cbCompleted.buttonTintList = ColorStateList.valueOf(
                    getCheckboxColorForMediumPriority()
                )
                tvTask.setTextColor(getDefaultTextColor())
            }
            2 -> {
                cbCompleted.buttonTintList = ColorStateList.valueOf(
                    getCheckboxColorForLowPriority()
                )
                tvTask.setTextColor(getLowPriorityTextColor())
            }
            else -> {
                cbCompleted.buttonTintList = ColorStateList.valueOf(
                    getCheckboxColorForMediumPriority()
                )
                tvTask.setTextColor(getDefaultTextColor())
            }
        }

        if (task.isCompleted) {
            tvTask.paintFlags = tvTask.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvTask.alpha = 0.6f
            cbCompleted.alpha = 0.6f
        } else {
            tvTask.paintFlags = tvTask.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tvTask.alpha = 1f
            cbCompleted.alpha = 1f
        }

        // Обработчик изменения состояния чекбокса
        cbCompleted.setOnCheckedChangeListener { _, isChecked ->

            val wasCompleted = task.isCompleted
            val pointsDelta = when {
                isChecked && !wasCompleted -> when (task.priority) { 0 -> 3 1 -> 2 else -> 1 }
                !isChecked && wasCompleted -> when (task.priority) { 0 -> -3 1 -> -2 else -> -1 }
                else -> 0 }

            if (pointsDelta != 0) {
                lifecycleScope.launch { scoreRepository.updatePoints(userId, pointsDelta)
                    (activity as? MainActivity)?.updateScoreDisplay() } }
            // 1. Анимация вычеркивания
            ValueAnimator.ofInt(0, tvTask.width).apply { 
                addUpdateListener { tvTask.paintFlags = tvTask.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG }
                start() }

            // 2. Анимация исчезновения
            taskView.animate() .alpha(0f) .translationYBy(-20f) .setDuration(300).withEndAction {
                    (taskView.parent as? ViewGroup)?.removeView(taskView)
                    lifecycleScope.launch {
                        try { taskRepository.toggleTaskCompletion(task.id, isChecked)
                            // Обновление списка без задержки
                            withContext(Dispatchers.Main) { loadTasks(auth.currentUser?.uid ?: "") }
                        } catch (e: Exception) { activity?.runOnUiThread {
                                binding.layoutTaskList.addView(taskView)
                                taskView.alpha = 1f
                                taskView.translationY = 0f
                                cbCompleted.isChecked = !isChecked
                            }
                        }
                    }
                }
                .start()
            if (isChecked) {
                vibrator.vibrate(100) // Вибрация на 100 миллисекунд
            }
        }

        taskView.setOnClickListener {
            if (!isArchived) showEditTaskDialog(task)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление задачи")
                .setMessage("Вы уверены, что хотите удалить эту задачу?")
                .setPositiveButton("Удалить") { _, _ ->
                    taskView.animate()
                        .alpha(0f)
                        .translationYBy(-20f)
                        .setDuration(300)
                        .withEndAction {
                            (taskView.parent as? ViewGroup)?.removeView(taskView)
                            lifecycleScope.launch {
                                // Только локальное удаление
                                taskRepository.deleteTask(task.id)
                            }
                        }
                        .start()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        return taskView
    }

    private fun toggleArchiveVisibility() {
        isArchiveExpanded = !isArchiveExpanded

        // Анимация кнопки
        binding.btnToggleArchive.animate()
            .rotation(if (isArchiveExpanded) 180f else 0f)
            .setDuration(300)
            .start()

        // Показываем/скрываем архив
        binding.layoutArchiveList.visibility = if (isArchiveExpanded) View.VISIBLE else View.GONE

        // Загружаем архивные задачи при первом открытии
        if (isArchiveExpanded && binding.layoutArchiveList.childCount == 0) {
            lifecycleScope.launch {
                try {
                    val userId = auth.currentUser?.uid ?: return@launch
                    val archivedTasks = taskRepository.getTasks(userId).filter { it.isCompleted }
                    activity?.runOnUiThread {
                        archivedTasks.forEach { task ->
                            binding.layoutArchiveList.addView(createTaskView(task, true))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TaskFragment", "Error loading archived tasks", e)
                }
            }
        }
    }

    private fun updateArchiveControls(hasArchivedTasks: Boolean) {
        if (hasArchivedTasks) {
            binding.btnToggleArchive.visibility = View.VISIBLE
            binding.btnDeleteAllArchivedTasks.visibility = View.VISIBLE
            binding.tvArchiveTitle.visibility = View.VISIBLE
        } else {
            binding.btnToggleArchive.visibility = View.GONE
            binding.btnDeleteAllArchivedTasks.visibility = View.GONE
            binding.tvArchiveTitle.visibility = View.GONE
        }
    }

    private fun getDefaultTextColor(): Int {
        return if (isDarkTheme()) {
            ContextCompat.getColor(requireContext(), R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }
    }

    private fun getLowPriorityTextColor(): Int {
        return if (isDarkTheme()) {
            ContextCompat.getColor(requireContext(), R.color.textVariantsColorDark)
        } else {
            ContextCompat.getColor(requireContext(), R.color.textVariantsColor)
        }
    }

    private fun getCheckboxColorForMediumPriority(): Int {
        return if (isDarkTheme()) {
            ContextCompat.getColor(requireContext(), R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }
    }

    private fun getCheckboxColorForLowPriority(): Int {
        return ContextCompat.getColor(requireContext(), R.color.checkbox_normal)
    }

    private fun isDarkTheme(): Boolean {
        return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun showAddTaskDialog(userId: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.etTaskTitle)
        val spinnerPriority = dialogView.findViewById<Spinner>(R.id.spinnerPriority)

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить задачу")
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val taskTitle = editTitle.text.toString()
                if (taskTitle.isBlank()) {
                    Toast.makeText(requireContext(), "Введите название задачи", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        taskRepository.addTask(Task(
                            title = taskTitle,
                            priority = spinnerPriority.selectedItemPosition,
                            userId = userId,
                            updatedAt = Timestamp.now()
                        ))
                        loadTasks(userId)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_task, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.etEditTitle)
        val spinnerPriority = dialogView.findViewById<Spinner>(R.id.spinnerEditPriority)

        editTitle.setText(task.title)
        spinnerPriority.setSelection(task.priority)

        AlertDialog.Builder(requireContext())
            .setTitle("Редактировать задачу")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        taskRepository.updateTask(task.copy(
                            title = editTitle.text.toString(),
                            priority = spinnerPriority.selectedItemPosition
                        ))
                        loadTasks(auth.currentUser?.uid ?: "")
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Ошибка обновления", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showLoginScreen() {
        startActivity(Intent(requireContext(), AuthActivity::class.java))
        requireActivity().finish()
        requireActivity().overridePendingTransition(
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }
}