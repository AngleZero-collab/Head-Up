package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.poselandmarker.HeadUpApiClient
import com.google.mediapipe.examples.poselandmarker.HeadUpAuthStore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.RegisterRequest
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.guestButton.setOnClickListener { navigateToNext() }
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (!validateInput(email, password)) return@setOnClickListener
            loginOrRegister(email, password)
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), R.string.login_invalid_email, Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 8) {
            Toast.makeText(requireContext(), R.string.login_invalid_password, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun loginOrRegister(email: String, password: String) {
        binding.loginButton.isEnabled = false
        binding.loginButton.setText(R.string.login_connecting)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = try {
                    HeadUpApiClient.service.login(email, password)
                } catch (error: HttpException) {
                    if (error.code() != 401) throw error
                    HeadUpApiClient.service.register(RegisterRequest(email, password))
                    HeadUpApiClient.service.login(email, password)
                }
                HeadUpAuthStore.saveSession(requireContext(), token.accessToken, token.userId)
                Toast.makeText(requireContext(), R.string.login_success, Toast.LENGTH_SHORT).show()
                navigateToNext()
            } catch (error: Exception) {
                Toast.makeText(requireContext(), getString(R.string.login_failed, error.message), Toast.LENGTH_LONG).show()
            } finally {
                _binding?.loginButton?.isEnabled = true
                _binding?.loginButton?.setText(R.string.login_button)
            }
        }
    }

    private fun navigateToNext() {
        findNavController().navigate(R.id.action_login_to_permissions)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
