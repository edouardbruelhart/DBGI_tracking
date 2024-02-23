// Activity that permits to add falcons in the database and associate them to a rack.

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
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.PreviewView
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
@Suppress("DEPRECATION")
class FalconActivity : AppCompatActivity() {

    // Initiate the displayed objects
    private lateinit var scanButtonRack: Button
    private lateinit var emptyPlace: TextView
    private lateinit var textFalcon: TextView
    private lateinit var scanButtonFalcon: Button
    private lateinit var previewView: PreviewView
    private lateinit var flashlightButton: Button
    private lateinit var scanStatus: TextView

    private var isRackScanActive = false
    private var isObjectScanActive = false
    private var hasTriedAgain = false
    private var isQrScannerActive = false
    private var lastAccessToken: String? = null

    @OptIn(ExperimentalGetImage::class) @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the connection with the XML file to add the displayed objects
        setContentView(R.layout.activity_falcon)

        title = "Falcon mode"

        // Add the back arrow to this screen
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize objects views
        scanButtonRack = findViewById(R.id.scanButtonRack)
        emptyPlace = findViewById(R.id.emptyPlace)
        textFalcon = findViewById(R.id.textFalcon)
        scanButtonFalcon = findViewById(R.id.scanButtonFalcon)
        previewView = findViewById(R.id.previewView)
        flashlightButton = findViewById(R.id.flashlightButton)
        scanStatus = findViewById(R.id.scanStatus)

        val token = intent.getStringExtra("ACCESS_TOKEN").toString()

        // stores the original token
        retrieveToken(token)

        // Set up button click listener for Rack QR Scanner
        scanButtonRack.setOnClickListener {
            isRackScanActive = true
            isObjectScanActive = false
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan a rack"
            flashlightButton.visibility = View.VISIBLE
            scanButtonRack.visibility = View.INVISIBLE
            textFalcon.visibility = View.INVISIBLE
            scanButtonFalcon.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedRack ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                scanButtonRack.visibility = View.VISIBLE
                textFalcon.visibility = View.VISIBLE
                scanButtonFalcon.visibility = View.VISIBLE
                scanStatus.text = ""
                scanButtonRack.text = scannedRack
                manageScan()
            }
        }

        // Set up button click listener for falcon QR Scanner
        scanButtonFalcon.setOnClickListener {
            isRackScanActive = false
            isObjectScanActive = true
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan a falcon"
            flashlightButton.visibility = View.VISIBLE
            scanButtonRack.visibility = View.INVISIBLE
            textFalcon.visibility = View.INVISIBLE
            scanButtonFalcon.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedSample ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                scanButtonRack.visibility = View.VISIBLE
                textFalcon.visibility = View.VISIBLE
                scanButtonFalcon.visibility = View.VISIBLE
                scanStatus.text = ""
                scanButtonFalcon.text = scannedSample
                manageScan()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun manageScan() {

        // Counts the spaces left in the rack
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                        if (isRackScanActive) {
                            // permits to calculate places in container using columnsxrows present in container name
                            val rack = scanButtonRack.text.toString()
                            val rackValue = checkRackLoad(rack)
                            val parts = rack.split("_")
                            val size = parts[1]
                            val sizeNumber = size.split("x")
                            val places = sizeNumber[0].toInt() * sizeNumber[1].toInt()
                            val stillPlace = places - rackValue
                            if (rackValue >= 0 && stillPlace > 0){
                                textFalcon.visibility = View.VISIBLE
                                scanButtonFalcon.visibility = View.VISIBLE
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.setTextColor(Color.GRAY)
                                emptyPlace.text = "This rack should still contain $stillPlace empty places"
                            } else if (stillPlace < 1){
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.text = "This rack is full, please scan another one"
                                scanButtonRack.text = "Value"
                                scanButtonFalcon.text = "Begin to scan samples"
                                emptyPlace.setTextColor(Color.RED)
                            } else {
                                showToast("Connection error")
                                goToConnectionActivity()
                            }
                        } else if (isObjectScanActive) {
                            val rackId = scanButtonRack.text.toString()
                            val sampleId = scanButtonFalcon.text.toString()
                            withContext(Dispatchers.IO) {
                                sendDataToDirectus(sampleId, rackId)
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
                        // Retry the operation with the new access token
                        return@withContext checkRackLoad(rackId)
                    } else {
                        showToast("Connection error")
                        goToConnectionActivity()
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
                    val parts = rack.split("_")
                    val size = parts[1]
                    val sizeNumber = size.split("x")
                    val places = sizeNumber[0].toInt() * sizeNumber[1].toInt()
                    val upStillPlace = places - upRackValue

                    withContext(Dispatchers.Main) {

                        if(upStillPlace > 0){
                            // Automatically launch the QR scanning when last sample correctly added to the database
                            emptyPlace.visibility = View.VISIBLE
                            emptyPlace.text = "This rack should still contain $upStillPlace empty places"
                            hasTriedAgain = false
                            delay(500)
                            scanButtonFalcon.performClick()
                        } else {
                            emptyPlace.text = "Rack is full, scan another one to continue"
                            scanButtonRack.text = "scan another rack"
                            scanButtonFalcon.text = "Begin to scan samples"
                            textFalcon.visibility = View.INVISIBLE
                            scanButtonFalcon.visibility = View.INVISIBLE

                        }

                    }
                }
            } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        // Retry the operation with the new access token
                        return sendDataToDirectus(sampleId, rackId)
                    }
                // Request failed
                showToast("Connection error")
                goToConnectionActivity()
            } else {
                updateDataToDirectus(sampleId, rackId)
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun updateDataToDirectus(
        sampleId: String,
        rackId: String
    ) {
        // Define the table url
        val accessToken = retrieveToken()
        val collectionUrl = "http://directus.dbgi.org/items/Field_Samples/$sampleId"
        val url = URL(collectionUrl)
        val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

        // Perform the POST request to add the values on directus
        try {
            urlConnection.requestMethod = "PATCH"
            urlConnection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            urlConnection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )

            val data = JSONObject().apply {
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
                showToast("Database correctly updated")

                // Check if there is still enough place in the rack before initiating the QR code reader
                CoroutineScope(Dispatchers.IO).launch {
                    val rack = scanButtonRack.text.toString()
                    val upRackValue = checkRackLoad(rack)
                    val parts = rack.split("_")
                    val size = parts[1]
                    val sizeNumber = size.split("x")
                    val places = sizeNumber[0].toInt() * sizeNumber[1].toInt()
                    val upStillPlace = places - upRackValue

                    withContext(Dispatchers.Main) {

                        if(upStillPlace > 0){
                            // Automatically launch the QR scanning when last sample correctly added to the database
                            emptyPlace.visibility = View.VISIBLE
                            emptyPlace.text = "This rack should still contain $upStillPlace empty places"
                            hasTriedAgain = false
                            delay(500)
                            scanButtonFalcon.performClick()
                        } else {
                            emptyPlace.text = "Rack is full, scan another one to continue"
                            scanButtonRack.text = "scan another rack"
                            scanButtonFalcon.text = "Begin to scan samples"
                            textFalcon.visibility = View.INVISIBLE
                            scanButtonFalcon.visibility = View.INVISIBLE

                        }

                    }
                }
            } else if (!hasTriedAgain) {
                hasTriedAgain = true
                val newAccessToken = getNewAccessToken()

                if (newAccessToken != null) {
                    retrieveToken(newAccessToken)
                    // Retry the operation with the new access token
                    return sendDataToDirectus(sampleId, rackId)
                }
                // Request failed
                showToast("Connection error")
                goToConnectionActivity()
            } else {
                showToast("unknown error")
                goToConnectionActivity()
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    // Connect the back arrow to the action to go back to home page
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                return if (isQrScannerActive){
                    QRCodeScannerUtility.stopScanning()
                    isQrScannerActive = false
                    previewView.visibility = View.INVISIBLE
                    flashlightButton.visibility = View.INVISIBLE
                    scanButtonRack.visibility = View.VISIBLE
                    scanStatus.text = ""
                    if (isObjectScanActive){
                        textFalcon.visibility = View.VISIBLE
                        scanButtonFalcon.visibility = View.VISIBLE
                    }
                    true
                } else {
                    onBackPressed()
                    true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // If actual access token is deprecated, perform a connection to obtain a new one.
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
                    showToast("Connection error")
                    goToConnectionActivity()
                }
            }catch (e: Exception) {
                e.printStackTrace()
                showToast("$e")
                goToConnectionActivity()
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

    private fun goToConnectionActivity(){
        val intent = Intent(this, DirectusConnectionActivity::class.java)
        startActivity(intent)
    }
}