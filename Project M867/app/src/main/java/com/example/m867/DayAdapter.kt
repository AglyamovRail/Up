package com.example.m867

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.m867.Habit.Habit
import com.example.m867.Habit.HabitRecord
import com.google.android.flexbox.FlexboxLayout
import java.text.SimpleDateFormat
import java.util.*

class DayAdapter(
    private val days: List<Date>,
    private var allowPastDays: Boolean = true
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    val habitRecords = mutableMapOf<String, MutableList<HabitRecord>>()
    private val habits = mutableListOf<Habit>()
    private val habitCreated = mutableMapOf<String, Date>()

    var onHabitToggled: ((habitId: String, date: String, isCompleted: Boolean) -> Unit)? = null

    inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayOfWeek: TextView = view.findViewById(R.id.tvDayOfWeek)
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        val layoutHabitDots: FlexboxLayout = view.findViewById(R.id.layoutHabitDots)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val date = days[position]
        val dayOfWeekFormat = SimpleDateFormat("EE", Locale("ru"))
        val dayNumberFormat = SimpleDateFormat("d", Locale("ru"))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        holder.tvDayOfWeek.text = dayOfWeekFormat.format(date)
        holder.tvDayNumber.text = dayNumberFormat.format(date)
        holder.layoutHabitDots.removeAllViews()

        val dayString = dateFormat.format(date)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        for (habit in habits) {
            val createdDate = habitCreated[habit.id] ?: continue
            if (date.before(createdDate)) continue

            val daysDiff = ((date.time - createdDate.time) / (1000 * 60 * 60 * 24)).toInt()
            val shouldShowDot = when (habit.periodType) {
                0 -> true
                1 -> daysDiff % 2 == 0
                2 -> daysDiff % habit.periodDays == 0
                else -> true
            }

            if (shouldShowDot) {
                val isCompleted = habitRecords[habit.id]?.any { it.date == dayString && it.isCompleted } == true
                val dot = View(holder.itemView.context).apply {
                    layoutParams = FlexboxLayout.LayoutParams(56, 56).apply {
                        setMargins(8, 2, 8, 2)
                    }

                    // Создаем кружок с обводкой
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (isCompleted) Color.parseColor(habit.colorHex)
                            else ContextCompat.getColor(context, R.color.dot_inactive)
                        )
                        setStroke(
                            2, // толщина обводки в пикселях
                            ContextCompat.getColor(context, R.color.dot_border)
                        )
                    }

                    setOnClickListener {
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        if (allowPastDays || dayString == todayStr) { // Сравниваем строки дат
                            onHabitToggled?.invoke(habit.id, dayString, !isCompleted)
                        } else {
                            showOnlyTodayToast(context)
                        }
                    }

                    setOnLongClickListener {
                        if (date != today) {
                            Toast.makeText(
                                context,
                                "Для восстановления используйте кнопку восстановления",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                }
                holder.layoutHabitDots.addView(dot)
            }
        }
    }

    override fun getItemCount(): Int = days.size

    fun updateHabits(newHabits: List<Habit>) {
        habits.clear()
        habits.addAll(newHabits)
        habitCreated.clear()
        newHabits.forEach { h ->
            val raw = h.createdAt.toDate()
            val cal = Calendar.getInstance().apply {
                time = raw
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            habitCreated[h.id] = cal.time
        }
        notifyDataSetChanged()
    }

    fun updateRecords(records: List<HabitRecord>) {
        habitRecords.clear()
        records.forEach { record ->
            habitRecords.getOrPut(record.habitId) { mutableListOf() }.add(record)
        }
        notifyDataSetChanged()
    }

    fun setAllowPastDays(allow: Boolean) {
        allowPastDays = allow
        notifyDataSetChanged()
    }



    private fun showOnlyTodayToast(context: Context) {
        Toast.makeText(
            context,
            "Можно отмечать только сегодня. Используйте кнопку восстановления!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isToday(date: Date): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        return date == today
    }
}