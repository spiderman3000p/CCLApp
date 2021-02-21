package com.tautech.cclapp.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.tautech.cclapp.R
import kotlinx.android.synthetic.main.activity_login2.*

class Login2Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)
        loginBtn2.setOnClickListener {
            val username = usernameEt.text.toString()
            val pass = passwordEt.text.toString()
        }
    }
}