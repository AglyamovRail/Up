package com.example.m867

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.m867.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class RegisterFragment : Fragment() {
    private lateinit var binding: FragmentRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        setupRegisterButton()
        setupBackToLoginButton()
    }

    private fun setupRegisterButton() {
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val name = binding.etName.text.toString().trim()

            when {
                email.isEmpty() -> showError("Введите email")
                !isValidEmail(email) -> showError("Введите корректный email")
                password.isEmpty() -> showError("Введите пароль")
                password.length < 6 -> showError("Пароль должен содержать минимум 6 символов")
                name.length < 3 -> showError("Имя должно содержать минимум 3 символа")
                name.isEmpty() -> showError("Введите ваше имя")
                else -> registerUser(email, password, name)
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val pattern = Patterns.EMAIL_ADDRESS
        return pattern.matcher(email).matches()
    }

    private fun setupBackToLoginButton() {
        binding.btnBackToLogin.setOnClickListener {
            // Возвращаемся к фрагменту логина с анимацией
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                .replace(R.id.auth_container, LoginFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun registerUser(email: String, password: String, name: String) {
        binding.btnRegister.isEnabled = false // Блокируем кнопку во время регистрации

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateUserProfile(name)
                } else {
                    binding.btnRegister.isEnabled = true
                    showError("Ошибка регистрации: ${task.exception?.message}")
                }
            }
    }

    private fun updateUserProfile(name: String) {
        val user = auth.currentUser
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()

        user?.updateProfile(profileUpdates)
            ?.addOnCompleteListener { task ->
                binding.btnRegister.isEnabled = true
                if (task.isSuccessful) {
                    // Сразу переходим в MainActivity
                    (activity as? AuthActivity)?.startMainActivity()
                } else {
                    showError("Не удалось сохранить имя пользователя")
                }
            }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}