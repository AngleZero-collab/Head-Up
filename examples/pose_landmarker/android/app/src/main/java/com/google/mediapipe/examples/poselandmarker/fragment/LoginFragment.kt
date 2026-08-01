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

        binding.guestButton.setOnClickListener {
            binding.guestButton.isEnabled = false
            HeadUpAuthStore.startGuestSession(requireContext())
            Toast.makeText(requireContext(), R.string.login_guest_started, Toast.LENGTH_SHORT).show()
            navigateToNext()
        }
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (!validateInput(email, password)) return@setOnClickListener
            login(email, password)
        }
        binding.registerButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (!validateInput(email, password)) return@setOnClickListener
            register(email, password)
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

    private fun login(email: String, password: String) {
        setAuthButtonsEnabled(false)
        binding.loginButton.setText(R.string.login_connecting)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = HeadUpApiClient.service.login(email, password)
                saveToken(token)
                Toast.makeText(requireContext(), R.string.login_success, Toast.LENGTH_SHORT).show()
                navigateToNext()
            } catch (error: HttpException) {
                val message = if (error.code() == 401) {
                    getString(R.string.login_bad_credentials)
                } else {
                    getString(R.string.login_failed, "HTTP ${error.code()}")
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(requireContext(), getString(R.string.login_failed, error.message), Toast.LENGTH_LONG).show()
            } finally {
                setAuthButtonsEnabled(true)
                _binding?.loginButton?.setText(R.string.login_button)
            }
        }
    }

    private fun register(email: String, password: String) {
        setAuthButtonsEnabled(false)
        binding.registerButton.setText(R.string.register_connecting)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = HeadUpApiClient.service.register(RegisterRequest(email, password))
                if (!response.isSuccessful) {
                    val message = if (response.code() == 409) {
                        getString(R.string.register_email_exists)
                    } else {
                        getString(R.string.register_failed, "HTTP ${response.code()}")
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val token = HeadUpApiClient.service.login(email, password)
                saveToken(token)
                Toast.makeText(requireContext(), R.string.register_success, Toast.LENGTH_SHORT).show()
                navigateToNext()
            } catch (error: Exception) {
                Toast.makeText(requireContext(), getString(R.string.register_failed, error.message), Toast.LENGTH_LONG).show()
            } finally {
                setAuthButtonsEnabled(true)
                _binding?.registerButton?.setText(R.string.register_button)
            }
        }
    }

    private fun saveToken(token: com.google.mediapipe.examples.poselandmarker.TokenResponse) {
        HeadUpAuthStore.saveSession(
            requireContext(),
            token.accessToken,
            token.userId,
            token.subscriptionTier,
            token.role,
        )
    }

    private fun setAuthButtonsEnabled(enabled: Boolean) {
        _binding?.loginButton?.isEnabled = enabled
        _binding?.registerButton?.isEnabled = enabled
        _binding?.guestButton?.isEnabled = enabled
    }

    private fun navigateToNext() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.login_fragment) {
            navController.navigate(R.id.action_login_to_permissions)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
