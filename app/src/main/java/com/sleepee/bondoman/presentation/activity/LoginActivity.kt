package com.sleepee.bondoman.presentation.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sleepee.bondoman.R
import com.sleepee.bondoman.databinding.ActivityLoginBinding
import com.sleepee.bondoman.databinding.ActivityMainBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    @SuppressLint("SetTextI18n") // delete this later
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val loginButton = binding.loginButton

        val emailEditText = binding.emailInput
        val passwordEditText = binding.passwordInput

        // For ease of testing only
        emailEditText.setText("13521085@std.stei.itb.ac.id")
        passwordEditText.setText("password_13521085")

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            login(email, password)
        }
    }

    private fun login(email : String, password : String) {
        // Validate email and password
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill the email and password field", Toast.LENGTH_SHORT).show()
            return
        }

        if (Patterns.EMAIL_ADDRESS.matcher(email).matches().not()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return
        }


        val mainActivityIntent = Intent(this, MainActivity::class.java)
        startActivity(mainActivityIntent)
    }
}
