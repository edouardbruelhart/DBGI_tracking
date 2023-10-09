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
@Suppress("NAME_SHADOWING")
class SampleActivity : AppCompatActivity() {

    // Initiate the displayed objects
    private lateinit var sampleMethodRack: TextView
    private lateinit var scanButtonRack: Button
    private lateinit var emptyPlace: TextView
    private lateinit var scanButtonSample: Button
    private var isRackScanActive = false
    private var isObjectScanActive = false
    var hasTriedAgain = false

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Create the connection with the XML file to add the displayed objects
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        // Add the back arrow to this screen
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize objects views
        sampleMethodRack = findViewById(R.id.sampleMethodRack)
        scanButtonRack = findViewById(R.id.scanButtonRack)
        emptyPlace = findViewById(R.id.emptyPlace)
        scanButtonSample = findViewById(R.id.scanButtonSample)

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

    @SuppressLint("SetTextI18n")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Counts the spaces left in the rack
        CoroutineScope(Dispatchers.IO).launch {
            val access_token = intent.getStringExtra("ACCESS_TOKEN").toString()
            val rackValue = checkRackLoad(access_token)
            val EmptyPlace = 24 - rackValue

            withContext(Dispatchers.Main) {
                if (requestCode == IntentIntegrator.REQUEST_CODE) {
                    val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

                    // Initiate the activity when a QR is scanned
                    if (result != null && result.contents != null) {
                        if (rackValue >= 0 && EmptyPlace > 0) {
                            if (isRackScanActive) {
                                scanButtonRack.text = "Value"
                                scanButtonRack.text = result.contents
                                scanButtonSample.visibility = View.VISIBLE
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.text = "This rack should still contain $EmptyPlace empty places"
                            } else if (isObjectScanActive) {
                                scanButtonSample.text = result.contents
                                val access_token = intent.getStringExtra("ACCESS_TOKEN")
                                val rackId = scanButtonRack.text.toString()
                                val sampleId = scanButtonSample.text.toString()
                                if (access_token != null) {
                                    withContext(Dispatchers.IO) {sendDataToDirectus(access_token, sampleId, rackId)}
                                }
                            }
                        } else {
                            handleInvalidScanResult(EmptyPlace, rackValue)
                        }
                    }
                }
            }
        }
    }

    // Function to send data to Directus
    @SuppressLint("SetTextI18n")
    private suspend fun sendDataToDirectus(
        access_token: String,
        sampleId: String,
        rackId: String
    ) {
        // Define the table url
        val collection_url = "http://directus.dbgi.org/items/Field_Samples"
        val url = URL(collection_url)
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
                "Bearer $access_token"
            )

            val data = JSONObject().apply {
                put("field_sample_id", sampleId)
                put("container_8x3_id", rackId)
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

                withContext(Dispatchers.Main) {
                    // Display a Toast with the response message
                    Toast.makeText(
                        this@SampleActivity,
                        "$sampleId correctly added to database",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Check if there is still enough place in the rack before initiating the QR code reader
                CoroutineScope(Dispatchers.IO).launch {
                    val access_token = intent.getStringExtra("ACCESS_TOKEN").toString()
                    val upRackValue = checkRackLoad(access_token)
                    val upEmptyPlace = 24 - upRackValue

                    withContext(Dispatchers.Main) {

                        if(upEmptyPlace > 0){
                            // Automatically launch the QR scanning when last sample correctly added to the database
                            emptyPlace.visibility = View.VISIBLE
                            emptyPlace.text = "This rack should still contain $upEmptyPlace empty places"
                            hasTriedAgain = false
                            delay(1500)
                            startQRScan("Scan object's QR")
                        } else {
                            emptyPlace.text = "Rack is full, scan another one to continue"
                            scanButtonRack.text = "Value"
                            scanButtonSample.text = "Begin to scan samples"
                            scanButtonSample.visibility = View.INVISIBLE

                        }

                    }
                }
            } else {
                if (hasTriedAgain == false) {
                    hasTriedAgain = true
                    val new_access_token = getNewAccessToken()

                    if (new_access_token != null) {
                        // Retry the operation with the new access token
                        return sendDataToDirectus(new_access_token, sampleId, rackId)
                    }

                }
                // Request failed
                withContext(Dispatchers.Main) {
                    // Display a Toast with an error message
                    Toast.makeText(
                        this@SampleActivity,
                        "Request failed, sample is already in the database",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    // Manage errors information to guide the user
    @SuppressLint("SetTextI18n")
    private fun handleInvalidScanResult(EmptyPlace: Int, rackValue: Int) {
        emptyPlace.visibility = View.VISIBLE
        when {
            EmptyPlace < 1 -> {
                emptyPlace.text = "This rack is full, please scan another one"
                scanButtonSample.text = "Begin to scan samples"
            }
            rackValue < 0 -> {
                emptyPlace.text = "Database error, please check your connection."
            }
        }
        emptyPlace.setTextColor(Color.RED)
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

    // Function to ask how many samples are already present in the rack to directus
    private suspend fun checkRackLoad(access_token: String): Int {
        val rackId = scanButtonRack.text

        val url =
            URL("http://directus.dbgi.org/items/Field_Samples/?filter[container_8x3_id][_eq]=$rackId")

        val urlConnection =
            withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

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

                // Parse the response JSON to get the count
                val jsonObject = JSONObject(response.toString())
                val dataArray = jsonObject.getJSONArray("data")

                // Return the number of sorted elements
                return dataArray.length()
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Access token is invalid, attempt to reconnect
                // This is a simplified example, you should implement the logic to obtain a new access token

                val new_access_token = getNewAccessToken()

                if (new_access_token != null) {
                    // Retry the operation with the new access token
                    return checkRackLoad(new_access_token)
                }
            }
        } finally {
            urlConnection.disconnect()
        }

        return -1 // Return a value indicating an error
    }

    @SuppressLint("SetTextI18n")
    private suspend fun getNewAccessToken(): String? {
        // Start a coroutine to perform the network operation
        val deferred = CompletableDeferred<String?>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val base_url = "http://directus.dbgi.org"
                val login_url = "$base_url/auth/login"
                val url = URL(login_url)
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
                    val access_token = data.getString("access_token")
                    deferred.complete(access_token)
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
}

