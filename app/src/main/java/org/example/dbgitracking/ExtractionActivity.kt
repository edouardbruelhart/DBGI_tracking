@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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

@Suppress("NAME_SHADOWING")
class ExtractionActivity : AppCompatActivity() {

    private lateinit var newExtractionMethod: TextView
    private lateinit var extractionMethodLabel: TextView
    private lateinit var extractionMethodSpinner: Spinner
    private lateinit var extractionMethodBox: TextView
    private lateinit var extractionInformation: TextView
    private lateinit var scanButtonBox: Button
    private lateinit var scanButtonSample: Button

    private var choices: List<String> = mutableListOf("Choose an option")

    private var isBoxScanActive = false
    private var isObjectScanActive = false

    @SuppressLint("CutPasteId", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extraction)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        newExtractionMethod = findViewById(R.id.newExtractionMethod)
        extractionMethodLabel = findViewById(R.id.extractionMethodLabel)
        extractionMethodSpinner = findViewById(R.id.extractionMethodSpinner)
        extractionMethodBox = findViewById(R.id.extractionMethodBox)
        extractionInformation = findViewById(R.id.extractionInformation)
        scanButtonBox = findViewById(R.id.scanButtonBox)
        scanButtonSample = findViewById(R.id.scanButtonSample)

        // Make the link clickable
        val linkTextView: TextView = findViewById(R.id.newExtractionMethod)
        val spannableString = SpannableString(linkTextView.text)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val url = "http://directus.dbgi.org/admin/content/Extraction_Methods/+"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
        }
        spannableString.setSpan(clickableSpan, 56, 60, spannableString.length)
        linkTextView.text = spannableString
        linkTextView.movementMethod = LinkMovementMethod.getInstance()

        // Fetch values and populate spinner
        fetchValuesAndPopulateSpinner()

        extractionMethodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Check if a valid option (not "Choose an option") is selected
                    extractionMethodBox.visibility = View.VISIBLE
                    scanButtonBox.visibility = View.VISIBLE
                } else {
                    extractionMethodBox.visibility = View.INVISIBLE
                    scanButtonBox.visibility = View.INVISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed
            }
        }

        // Set up button click listener for Box QR Scanner
        scanButtonBox.setOnClickListener {
            isBoxScanActive = true
            isObjectScanActive = false
            startQRScan("Scan box's QR")
        }

        // Set up button click listener for Object QR Scanner
        scanButtonSample.setOnClickListener {
            isBoxScanActive = false
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
                if (isBoxScanActive) {
                    scanButtonBox.text = result.contents // Update the button text
                    scanButtonSample.visibility = View.VISIBLE // Show actionButton if valid
                } else if (isObjectScanActive) {
                    scanButtonSample.text = result.contents // Update the button text

                    // Function to send data to Directus
                    suspend fun sendDataToDirectus(access_token: String, extractId: String, boxId: String, extractionMethod: String) {
                        // Define the table url
                        val collection_url = "http://directus.dbgi.org/items/Lab_Extracts/$extractId"
                        val url = URL(collection_url)
                        val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

                        try {
                            urlConnection.requestMethod = "PATCH"
                            urlConnection.setRequestProperty("Content-Type", "application/json")
                            urlConnection.setRequestProperty("Authorization", "Bearer $access_token")

                            val data = JSONObject().apply {
                                put("container_9x9_id", boxId)
                                put("extraction_method", extractionMethod)
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
                                        this@ExtractionActivity,
                                        "Database correctly updated",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                // Start a coroutine to delay the next scan by 5 seconds
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(1500)
                                    startQRScan("Scan object's QR")
                                }
                            } else {
                                // Request failed
                                withContext(Dispatchers.Main) {
                                    // Display a Toast with an error message
                                    Toast.makeText(
                                        this@ExtractionActivity,
                                        "Request failed, no such extract in the database",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } finally {
                            urlConnection.disconnect()
                        }
                    }

                    // Usage
                    CoroutineScope(Dispatchers.IO).launch {
                        val access_token = intent.getStringExtra("ACCESS_TOKEN")
                        val selectedValue = extractionMethodSpinner.selectedItem.toString()

                        if (access_token != null) {
                            // Assuming 'scanButtonSample.text' and 'scanButtonRack.text' are already defined
                            sendDataToDirectus(access_token, scanButtonSample.text.toString(), scanButtonBox.text.toString(), selectedValue)
                        }
                    }
                }
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchValuesAndPopulateSpinner() {
        val access_token = intent.getStringExtra("ACCESS_TOKEN")

        val apiUrl = "http://directus.dbgi.org/items/Extraction_Methods" // Replace with your collection URL
        val url = URL(apiUrl)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $access_token")
                urlConnection.connect()

                val inputStream = urlConnection.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?

                while (bufferedReader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                bufferedReader.close()

                // Parse JSON response
                val jsonArray = JSONObject(response.toString()).getJSONArray("data")
                val values = ArrayList<String>()
                val descriptions = HashMap<String, String>()

                // Add "Choose an option" to the list of values
                values.add("Choose an option")

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val value = jsonObject.getString("method_name") // Replace with actual field name
                    val description = jsonObject.getString("method_description") // Replace with actual field name
                    values.add(value)
                    descriptions[value] = description
                }

                runOnUiThread {
                    // Populate spinner with values
                    choices = values // Update choices list
                    val adapter = ArrayAdapter(this@ExtractionActivity, android.R.layout.simple_spinner_item, values)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    extractionMethodSpinner.adapter = adapter

                    // Add an OnItemSelectedListener to update newExtractionMethod text and handle visibility
                    extractionMethodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position > 0) { // Check if a valid option (not "Choose an option") is selected
                                val selectedValue = values[position]
                                val selectedDescription = descriptions[selectedValue]
                                extractionInformation.visibility = View.VISIBLE
                                extractionInformation.text = selectedDescription
                                extractionMethodBox.visibility = View.VISIBLE
                                scanButtonBox.visibility = View.VISIBLE
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            //newExtractionMethod.text = "No suitable referenced method? add it by following this link and restart the application"
                            extractionMethodBox.visibility = View.INVISIBLE
                            scanButtonBox.visibility = View.INVISIBLE
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startQRScan(prompt: String) {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt(prompt)
        integrator.setCameraId(0)
        integrator.setOrientationLocked(false)
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(true)
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
