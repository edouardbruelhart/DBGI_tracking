@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InjectionActivity : AppCompatActivity() {

    private lateinit var injectionMethodLabel: TextView
    private lateinit var injectionMethodSpinner: Spinner
    private lateinit var scanButtonExtract: Button
    private var isObjectScanActive = false
    private lateinit var scannedInfoTextView: TextView

    // Define the extraction methods
    private val injectionMethods = arrayOf("Method 1", "Method 2", "Method 3")

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_injection)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        injectionMethodLabel = findViewById(R.id.injectionMethodLabel)
        injectionMethodSpinner = findViewById(R.id.injectionMethodSpinner)
        scanButtonExtract = findViewById(R.id.scanButtonExtract)
        scannedInfoTextView = findViewById(R.id.scannedInfoTextView)

        // Set up spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, injectionMethods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        injectionMethodSpinner.adapter = adapter

        // Set up button click listener for Object QR Scanner
        scanButtonExtract.setOnClickListener {
            isObjectScanActive = true
            startQRScan("Scan object's QR")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if (result != null && result.contents != null) {
                scanButtonExtract.text = result.contents // Update the button text
                // Display scanned information
                val info =
                    "Sample ${scanButtonExtract.text} injected with ${injectionMethodSpinner.selectedItem} has been added to database, printing injection label... "
                scannedInfoTextView.text = info
                scannedInfoTextView.visibility = View.VISIBLE

                // Start a coroutine to delay the next scan by 5 seconds
                run {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(10000)
                        startQRScan("Scan object's QR")
                    }
                }
            }
        }
    }

    private fun startQRScan(prompt: String) {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt(prompt)
        integrator.setCameraId(0)  // Use the default camera
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}