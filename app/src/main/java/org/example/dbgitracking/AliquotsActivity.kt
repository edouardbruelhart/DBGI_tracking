@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AliquotsActivity : AppCompatActivity() {

    private lateinit var aliquotsMethodLabel: TextView
    private lateinit var scanButtonExtract: Button
    private var isObjectScanActive = false
    private lateinit var scannedInfoTextView: TextView
    private lateinit var volumeInput: EditText
    private lateinit var actionButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aliquots)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        aliquotsMethodLabel = findViewById(R.id.aliquotsMethodLabel)
        scanButtonExtract = findViewById(R.id.scanButtonExtract)
        scannedInfoTextView = findViewById(R.id.scannedInfoTextView)
        volumeInput = findViewById(R.id.volumeInput)
        actionButton = findViewById(R.id.actionButton)

        // Set up button click listener for Object QR Scanner
        scanButtonExtract.setOnClickListener {
            isObjectScanActive = true
            startQRScan("Scan object's QR")
        }

        // Add a TextWatcher to the numberInput for real-time validation
        volumeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val inputText = s.toString()
                val inputNumber = inputText.toFloatOrNull()

                if (inputNumber != null) {
                    volumeInput.setBackgroundResource(android.R.color.transparent) // Set background to transparent if valid
                    actionButton.visibility = View.VISIBLE // Show actionButton if valid
                } else {
                    volumeInput.setBackgroundResource(android.R.color.holo_red_light) // Set background to red if not valid
                    actionButton.visibility = View.INVISIBLE // Hide actionButton if not valid
                }
            }
        })

        actionButton.setOnClickListener {
            val inputText = volumeInput.text.toString()
            val inputNumber = inputText.toFloatOrNull()

            if (inputNumber != null) {

                // Define the table url
                val collection_url = "http://directus.dbgi.org/items/Aliquots"

                // Function to send data to Directus
                suspend fun sendDataToDirectus(access_token: String, extractId: String, weight: String) {
                    val url = URL(collection_url)

                    val injectId = checkExistenceInDirectus(access_token, extractId)

                    if (injectId != null) {

                        val urlConnection =
                            withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

                        try {
                            urlConnection.requestMethod = "POST"
                            urlConnection.setRequestProperty("Content-Type", "application/json")
                            urlConnection.setRequestProperty(
                                "Authorization",
                                "Bearer $access_token"
                            )

                            val data = JSONObject().apply {
                                put("aliquot_id", injectId)
                                put("lab_extract_id", extractId)
                                put("aliquot_volume", weight)
                            }

                            val outputStream: OutputStream = urlConnection.outputStream
                            val writer = BufferedWriter(withContext(Dispatchers.IO) {
                                OutputStreamWriter(outputStream, "UTF-8")
                            })
                            withContext(Dispatchers.IO) {
                                writer.write(data.toString())
                            }
                            withContext(Dispatchers.IO) {
                                writer.flush()
                            }
                            withContext(Dispatchers.IO) {
                                writer.close()
                            }

                            val responseCode = urlConnection.responseCode
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val inputStream = urlConnection.inputStream
                                val bufferedReader = BufferedReader(
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        InputStreamReader(inputStream, "UTF-8")
                                    })
                                val response = StringBuilder()
                                var line: String?
                                while (withContext(Dispatchers.IO) {
                                        bufferedReader.readLine()
                                    }.also { line = it } != null) {
                                    response.append(line)
                                }
                                withContext(Dispatchers.IO) {
                                    bufferedReader.close()
                                }
                                withContext(Dispatchers.IO) {
                                    inputStream.close()
                                }

                                // 'response' contains the response from the server
                                withContext(Dispatchers.Main) {
                                    // Display a Toast with the response message
                                    Toast.makeText(
                                        this@AliquotsActivity,
                                        "$injectId correctly added to database",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                // Start a coroutine to delay the next scan by 5 seconds
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(500)
                                    startQRScan("Scan object's QR")
                                }
                            } else {
                                // Request failed
                                withContext(Dispatchers.Main) {
                                    // Display a Toast with an error message
                                    Toast.makeText(
                                        this@AliquotsActivity,
                                        "Request failed, check if code is correct",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } finally {
                            urlConnection.disconnect()
                        }
                    } else {
                        withContext(Dispatchers.Main){
                            Toast.makeText(
                                this@AliquotsActivity,
                                "No more available injection labels",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }



                // Usage
                CoroutineScope(Dispatchers.IO).launch {
                    val access_token = intent.getStringExtra("ACCESS_TOKEN")
                    if (access_token != null) {
                        // Assuming 'scanButtonSample.text' and 'scanButtonRack.text' are already defined
                        sendDataToDirectus(access_token, scanButtonExtract.text.toString(), inputNumber.toString())
                    } else {
                        Toast.makeText(
                            this@AliquotsActivity,
                            "Token error, please close the application and reconnect",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if (result != null && result.contents != null) {
                if (isObjectScanActive) {
                    scanButtonExtract.text = result.contents // Update the button text
                    volumeInput.visibility = View.VISIBLE
                }
            }
        }
    }

    suspend fun checkExistenceInDirectus(access_token: String, sampleId: String): String? {
        for (i in 1..99) {
            val testId = "${sampleId}_${String.format("%02d", i)}"
            val url = URL("http://directus.dbgi.org/items/Aliquots?filter[aliquot_id][_eq]=$testId")

            val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

            try {
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $access_token")

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response body
                    val inputStream = urlConnection.inputStream
                    val bufferedReader = BufferedReader(withContext(Dispatchers.IO) {
                        InputStreamReader(inputStream, "UTF-8")
                    })
                    val response = StringBuilder()
                    var line: String?
                    while (withContext(Dispatchers.IO) {
                            bufferedReader.readLine()
                        }.also { line = it } != null) {
                        response.append(line)
                    }
                    withContext(Dispatchers.IO) {
                        bufferedReader.close()
                    }
                    withContext(Dispatchers.IO) {
                        inputStream.close()
                    }

                    // Check if the response is empty
                    if (response.toString() == "{\"data\":[]}") {
                        return testId
                    }
                }
            } finally {
                urlConnection.disconnect()
            }
        }

        return null
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