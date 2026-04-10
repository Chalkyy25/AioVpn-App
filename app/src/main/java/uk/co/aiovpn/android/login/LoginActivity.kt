package uk.co.aiovpn.android.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import uk.co.aiovpn.android.R
import uk.co.aiovpn.android.home.HomeActivity
import uk.co.aiovpn.android.repo.VpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LoginActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_login)

        repo = VpnRepository(this)

        username = findViewById(R.id.aioUsername)
        password = findViewById(R.id.aioPassword)
        btn = findViewById(R.id.aioLoginBtn)

        btn.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        val u = username.text.toString().trim()
        val p = password.text.toString()

        if (u.isBlank() || p.isBlank()) {
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        btn.isEnabled = false
        btn.text = "Signing in..."

        Log.d("AIOVPN/Login", "Attempting login for user=$u")

        lifecycleScope.launch {
            var success = false
            try {
                withTimeout(25000) {
                    withContext(Dispatchers.IO) {
                        repo.login(u, p)
                    }
                }

                success = true

                if (!isFinishing && !isDestroyed) {
                    Log.d("AIOVPN/Login", "Login success, opening HomeActivity")

                    val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: TimeoutCancellationException) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login timed out. Check your connection.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("AIOVPN/Login", "Login failed", e)

                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@LoginActivity,
                        e.message ?: "Login failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                if (!success && !isFinishing && !isDestroyed) {
                    btn.isEnabled = true
                    btn.text = "Sign In"
                }
            }
        }
    }
}
