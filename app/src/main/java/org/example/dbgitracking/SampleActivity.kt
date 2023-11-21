// QR code reader is deprecated, this avoid warnings.
// Maybe a good point to change the QR code reader to a not deprecated one in the future.
@file:Suppress("DEPRECATION")

// Links the screen to the application
package org.example.dbgitracking

// Imports
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CompletableDeferred
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

// Create the class for the actual screen
class SampleActivity : AppCompatActivity() {

    // Initiate the displayed objects
    private lateinit var sampleMethodRack: TextView
    private lateinit var scanButtonRack: Button
    private lateinit var emptyPlace: TextView
    private lateinit var scanButtonSample: Button
    private var isRackScanActive = false
    private var isObjectScanActive = false
    private var hasTriedAgain = false
    private var lastAccessToken: String? = null

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the connection with the XML file to add the displayed objects
        setContentView(R.layout.activity_sample)

        // Add the back arrow to this screen
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize objects views
        sampleMethodRack = findViewById(R.id.sampleMethodRack)
        scanButtonRack = findViewById(R.id.scanButtonRack)
        emptyPlace = findViewById(R.id.emptyPlace)
        scanButtonSample = findViewById(R.id.scanButtonSample)

        val token = intent.getStringExtra("ACCESS_TOKEN").toString()

        // stores the original token
        retrieveToken(token)

        // Set up button click listener for Box QR Scanner
        scanButtonRack.setOnClickListener {
            isRackScanActive = true
            isObjectScanActive = false
            startQRScan("Scan rack's QR")
        }

        // Set up button click listener for Object QR Scanner
        scanButtonSample.setOnClickListener {
            isRackScanActive = false
            isObjectScanActive = true
            startQRScan("Scan object's QR")
        }
    }

    // Function to initiate the QR scan page
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

    @SuppressLint("SetTextI18n")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Counts the spaces left in the rack
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (requestCode == IntentIntegrator.REQUEST_CODE) {
                    val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

                    // Initiate the activity when a QR is scanned
                    if (result != null && result.contents != null) {
                        if (isRackScanActive) {
                            val rack = result.contents
                            val rackValue = checkRackLoad(rack)
                            val stillPlace = 24 - rackValue
                            if (rackValue >= 0 && stillPlace > 0){
                                scanButtonRack.text = result.contents
                                scanButtonSample.visibility = View.VISIBLE
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.setTextColor(Color.GRAY)
                                emptyPlace.text = "This rack should still contain $stillPlace empty places"
                            } else {
                                handleInvalidScanResult(stillPlace, rackValue)
                            }
                        } else if (isObjectScanActive) {
                            scanButtonSample.text = result.contents
                            val rackId = scanButtonRack.text.toString()
                            val sampleId = scanButtonSample.text.toString()
                            withContext(Dispatchers.IO) {
                                sendDataToDirectus(sampleId, rackId)
                            }
                        }
                    }
                }
            }
        }
    }

    // Function to ask how many samples are already present in the rack to directus
    private suspend fun checkRackLoad(rackId: String): Int {
        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url = URL("http://directus.dbgi.org/items/Field_Samples/?filter[mobile_container_id][_eq]=$rackId")
            val urlConnection = url.openConnection() as HttpURLConnection

            try {
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response body
                    val inputStream = urlConnection.inputStream
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    val response = StringBuilder()
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    // Parse the response JSON to get the count
                    val jsonObject = JSONObject(response.toString())
                    val dataArray = jsonObject.getJSONArray("data")

                    // Return the number of sorted elements
                    dataArray.length()
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        showToast("connection to directus lost, reconnecting...")
                        // Retry the operation with the new access token
                        return@withContext checkRackLoad(rackId)
                    } else {
                        showToast("Connection error")
                    }
                } else {
                    showToast("Error: $responseCode")
                }
            } finally {
                urlConnection.disconnect()
            } as Int
        }
    }

    // Function to send data to Directus
    @SuppressLint("SetTextI18n")
    private suspend fun sendDataToDirectus(
        sampleId: String,
        rackId: String
    ) {
        // Define the table url
        val accessToken = retrieveToken()
        val collectionUrl = "http://directus.dbgi.org/items/Field_Samples"
        val url = URL(collectionUrl)
        val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

        // Perform the POST request to add the values on directus
        try {
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            urlConnection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )

            val data = JSONObject().apply {
                put("field_sample_id", sampleId)
                put("mobile_container_id", rackId)
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

            // Capture the response code and control if it's successful
            val responseCode = urlConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                hasTriedAgain = false
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
                // Display a Toast with the response message
                showToast("$sampleId correctly added to database")

                // Check if there is still enough place in the rack before initiating the QR code reader
                CoroutineScope(Dispatchers.IO).launch {
                    val rack = scanButtonRack.text.toString()
                    val upRackValue = checkRackLoad(rack)
                    val upStillPlace = 24 - upRackValue

                    withContext(Dispatchers.Main) {

                        if(upStillPlace > 0){
                            // Automatically launch the QR scanning when last sample correctly added to the database
                            emptyPlace.visibility = View.VISIBLE
                            emptyPlace.text = "This rack should still contain $upStillPlace empty places"
                            hasTriedAgain = false
                            delay(1500)
                            startQRScan("Scan object's QR")
                        } else {
                            emptyPlace.text = "Rack is full, scan another one to continue"
                            scanButtonRack.text = "scan another rack"
                            scanButtonSample.text = "Begin to scan samples"
                            scanButtonSample.visibility = View.INVISIBLE

                        }

                    }
                }
            } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        showToast("connection to directus lost, reconnecting...")
                        // Retry the operation with the new access token
                        return sendDataToDirectus(sampleId, rackId)
                    }
                // Request failed
                showToast("Error: $responseCode")
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    // Manage errors information to guide the user
    @SuppressLint("SetTextI18n")
    private fun handleInvalidScanResult(stillPlace: Int, rackValue: Int) {
        emptyPlace.visibility = View.VISIBLE
        when {
            stillPlace < 1 -> {
                emptyPlace.text = "This rack is full, please scan another one"
                scanButtonRack.text = "Value"
                scanButtonSample.text = "Begin to scan samples"
            }
            rackValue < 0 -> {
                emptyPlace.text = "Database error, please check your connection."
            }
        }
        emptyPlace.setTextColor(Color.RED)
    }



    // Connect the back arrow to the action to go back to home page
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private suspend fun getNewAccessToken(): String? {
        // Start a coroutine to perform the network operation
        val deferred = CompletableDeferred<String?>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val baseUrl = "http://directus.dbgi.org"
                val loginUrl = "$baseUrl/auth/login"
                val url = URL(loginUrl)
                val connection =
                    withContext(Dispatchers.IO) {
                        url.openConnection()
                    } as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val requestBody = "{\"email\":\"$username\",\"password\":\"$password\"}"

                val outputStream: OutputStream = connection.outputStream
                withContext(Dispatchers.IO) {
                    outputStream.write(requestBody.toByteArray())
                }
                withContext(Dispatchers.IO) {
                    outputStream.close()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val `in` = BufferedReader(InputStreamReader(connection.inputStream))
                    val content = StringBuilder()
                    var inputLine: String?
                    while (withContext(Dispatchers.IO) {
                            `in`.readLine()
                        }.also { inputLine = it } != null) {
                        content.append(inputLine)
                    }
                    withContext(Dispatchers.IO) {
                        `in`.close()
                    }

                    val jsonData = content.toString()
                    val jsonResponse = JSONObject(jsonData)
                    val data = jsonResponse.getJSONObject("data")
                    val accessToken = data.getString("access_token")
                    deferred.complete(accessToken)
                } else {
                    emptyPlace.text = "Database error, please check your connection."
                    deferred.complete(null)
                }
            }catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    emptyPlace.text = "Database error, please check your connection."
                    deferred.complete(null)
                }
            }
        }
        return deferred.await()
    }

    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_LONG).show() }
    }

    private fun retrieveToken(token: String? = null): String {
        if (token != null) {
            lastAccessToken = token
        }
        return lastAccessToken ?: "null"
    }
}