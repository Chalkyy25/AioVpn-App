package uk.co.aiovpn.android.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import uk.co.aiovpn.android.home.HomeActivity
import uk.co.aiovpn.android.login.LoginActivity

class AuthGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = TokenStore(this).getTokenSync()

        val nextIntent = if (token.isNullOrBlank()) {
            Intent(this, LoginActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        startActivity(nextIntent)
        finish()
        overridePendingTransition(0, 0)
    }
}
