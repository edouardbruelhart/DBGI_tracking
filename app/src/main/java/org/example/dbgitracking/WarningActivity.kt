package org.example.dbgitracking

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WarningActivity : AppCompatActivity() {

    private lateinit var warningMessage: TextView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)

        // Initialize views
        warningMessage = findViewById(R.id.warningMessage)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val username = intent.getStringExtra("USERNAME")
        val password = intent.getStringExtra("PASSWORD")
        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")
        val activity = intent.getStringExtra("ACTIVITY").toString()

        // Set up button click listeners here
        confirmButton.setOnClickListener {
            when (activity) {
                "WeightingActivity" -> {
                    val intent = Intent(this, WeightingActivity::class.java)
                    intent.putExtra("ACCESS_TOKEN", accessToken)
                    intent.putExtra("USERNAME", username)
                    intent.putExtra("PASSWORD", password)
                    intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                    startActivity(intent)
                }
                "ExtractionActivity" -> {
                    val intent = Intent(this, ExtractionActivity::class.java)
                    intent.putExtra("ACCESS_TOKEN", accessToken)
                    intent.putExtra("USERNAME", username)
                    intent.putExtra("PASSWORD", password)
                    intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                    startActivity(intent)
                }
                "AliquotsActivity" -> {
                    val intent = Intent(this, AliquotsActivity::class.java)
                    intent.putExtra("ACCESS_TOKEN", accessToken)
                    intent.putExtra("USERNAME", username)
                    intent.putExtra("PASSWORD", password)
                    intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                    startActivity(intent)
                }
            }
        }

        // Set up button click listeners here
        cancelButton.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            intent.putExtra("USERNAME", username)
            intent.putExtra("PASSWORD", password)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            startActivity(intent)
        }


    }
}