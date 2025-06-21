package com.example.m867

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.m867.Habit.CalendarFragment
import com.example.m867.Score.ProfileFragment
import com.example.m867.Task.TaskFragment
import com.example.m867.Timer.PomodoroFragment

class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    // Колбек для сохранения ссылки на ProfileFragment
    var onProfileFragmentCreated: ((ProfileFragment) -> Unit)? = null

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TaskFragment()
            1 -> PomodoroFragment()
            2 -> CalendarFragment()
            3 -> ProfileFragment().also { fragment ->
                // Сохраняем ссылку на фрагмент профиля
                onProfileFragmentCreated?.invoke(fragment)
            }
            else -> TaskFragment()
        }
    }
}

