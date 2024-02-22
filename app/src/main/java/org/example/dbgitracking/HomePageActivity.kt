// Homepage screen that redirects to other screens when user chooses an action to perform

package org.example.dbgitracking

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage

class HomePageActivity : AppCompatActivity() {

    private lateinit var sampleButton: Button
    private lateinit var weightingButton: Button
    private lateinit var extractionButton: Button
    private lateinit var aliquotsButton: Button
    private lateinit var signalingButton: Button
    private lateinit var findButton: Button

    @OptIn(ExperimentalGetImage::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        sampleButton = findViewById(R.id.sampleButton)
        weightingButton = findViewById(R.id.weightingButton)
        extractionButton = findViewById(R.id.extractionButton)
        aliquotsButton = findViewById(R.id.aliquotsButton)
        signalingButton = findViewById(R.id.signalingButton)
        findButton = findViewById(R.id.findButton)

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val username = intent.getStringExtra("USERNAME")
        val password = intent.getStringExtra("PASSWORD")
        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

        // Set up button click listeners here
        sampleButton.setOnClickListener {
            val intent = Intent(this, SampleActivity::class.java)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            intent.putExtra("USERNAME", username)
            intent.putExtra("PASSWORD", password)
            startActivity(intent)
        }

        weightingButton.setOnClickListener {
            if (isPrinterConnected == "yes") {
                val intent = Intent(this, WeightingActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            } else {
                val activity = "WeightingActivity"
                val intent = Intent(this, WarningActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("ACTIVITY", activity)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            }
        }

        extractionButton.setOnClickListener {
            if (isPrinterConnected == "yes") {
                val intent = Intent(this, ExtractionActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            } else {
                val activity = "ExtractionActivity"
                val intent = Intent(this, WarningActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("ACTIVITY", activity)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            }
        }

        aliquotsButton.setOnClickListener {
            if (isPrinterConnected == "yes") {
                val intent = Intent(this, AliquotsActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            } else {
                val activity = "AliquotsActivity"
                val intent = Intent(this, WarningActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("ACTIVITY", activity)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            }
        }

        signalingButton.setOnClickListener {
            val intent = Intent(this, SignalingActivity::class.java)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            intent.putExtra("USERNAME", username)
            intent.putExtra("PASSWORD", password)
            startActivity(intent)
        }

        findButton.setOnClickListener {
            val intent = Intent(this, FindActivity::class.java)
            intent.putExtra("ACCESS_TOKEN", accessToken)
            intent.putExtra("USERNAME", username)
            intent.putExtra("PASSWORD", password)
            startActivity(intent)
        }
    }
}