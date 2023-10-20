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
import com.bradysdk.api.printerconnection.CutOption
import com.bradysdk.api.printerconnection.PrintingOptions
import com.bradysdk.printengine.templateinterface.TemplateFactory
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

class AliquotsActivity : AppCompatActivity() {

    private lateinit var scanBoxLabel: TextView
    private lateinit var scanButtonBox: Button
    private lateinit var aliquotsMethodLabel: TextView
    private lateinit var scanButtonExtract: Button
    private var isBoxScanActive = false
    private var isObjectScanActive = false
    private lateinit var scannedInfoTextView: TextView
    private lateinit var volumeInput: EditText
    private lateinit var actionButton: Button
    private var hasTriedAgain = false
    private var lastAccessToken: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aliquots)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        scanBoxLabel = findViewById(R.id.scanBoxLabel)
        scanButtonBox = findViewById(R.id.scanButtonBox)
        aliquotsMethodLabel = findViewById(R.id.aliquotsMethodLabel)
        scanButtonExtract = findViewById(R.id.scanButtonExtract)
        scannedInfoTextView = findViewById(R.id.scannedInfoTextView)
        volumeInput = findViewById(R.id.volumeInput)
        actionButton = findViewById(R.id.actionButton)

        val token = intent.getStringExtra("ACCESS_TOKEN").toString()

        // stores the original token
        retrieveToken(token)

        // Set up button click listener for Box QR Scanner
        scanButtonBox.setOnClickListener {
            isBoxScanActive = true
            isObjectScanActive = false
            startQRScan("Scan box's QR")
        }

        // Set up button click listener for Object QR Scanner
        scanButtonExtract.setOnClickListener {
            isBoxScanActive = false
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
                val collectionUrl = "http://directus.dbgi.org/items/Aliquots"

                // Function to send data to Directus
                @SuppressLint("DiscouragedApi")
                suspend fun sendDataToDirectus(extractId: String, volume: String, boxId: String) {

                    val accessToken = retrieveToken()
                    val url = URL(collectionUrl)

                    val injectId = checkExistenceInDirectus(extractId)

                    if (injectId != null) {

                        val urlConnection =
                            withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

                        try {
                            urlConnection.requestMethod = "POST"
                            urlConnection.setRequestProperty("Content-Type", "application/json")
                            urlConnection.setRequestProperty(
                                "Authorization",
                                "Bearer $accessToken"
                            )

                            val data = JSONObject().apply {
                                put("aliquot_id", injectId)
                                put("lab_extract_id", extractId)
                                put("aliquot_volume_microliter", volume)
                                put("container_9x9_id", boxId)
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

                                // 'response' contains the response from the server
                                showToast("$injectId correctly added to database")

                                // print label here
                                val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")
                                if (isPrinterConnected == "yes") {
                                    val printerDetails = PrinterDetailsSingleton.printerDetails
                                    // Specify the name of the template file you want to use.
                                    val selectedFileName = "template_dbgi"

                                    // Initialize an input stream by opening the specified file.
                                    val iStream = resources.openRawResource(
                                        resources.getIdentifier(
                                            selectedFileName, "raw",
                                            packageName
                                        )
                                    )
                                    val parts = injectId.split("_")
                                    val sample = "_" + parts[1]
                                    val extract = "_" + parts[2]
                                    val injetemp = "_" + parts[3]

                                    // Call the SDK method ".getTemplate()" to retrieve its Template Object
                                    val template =
                                        TemplateFactory.getTemplate(iStream, this@AliquotsActivity)
                                    // Simple way to iterate through any placeholders to set desired values.
                                    for (placeholder in template.templateData) {
                                        when (placeholder.name) {
                                            "QR" -> {
                                                placeholder.value = injectId
                                            }
                                            "sample" -> {
                                                placeholder.value = sample
                                            }
                                            "extract" -> {
                                                placeholder.value = extract
                                            }
                                            "injection/temp" -> {
                                                placeholder.value = injetemp
                                            }
                                        }
                                    }

                                    val printingOptions = PrintingOptions()
                                    printingOptions.cutOption = CutOption.EndOfJob
                                    printingOptions.numberOfCopies = 1
                                    val r = Runnable {
                                        runOnUiThread {
                                            printerDetails.print(
                                                this,
                                                template,
                                                printingOptions,
                                                null
                                            )
                                        }
                                    }
                                    val printThread = Thread(r)
                                    printThread.start()
                                }

                                // Start a coroutine to delay the next scan by 5 seconds
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(1500)
                                    startQRScan("Scan object's QR")
                                }
                            } else if (!hasTriedAgain) {
                                hasTriedAgain = true
                                val newAccessToken = getNewAccessToken()

                                if (newAccessToken != null) {
                                    retrieveToken(newAccessToken)
                                    showToast("connection to directus lost, reconnecting...")
                                    // Retry the operation with the new access token
                                    return sendDataToDirectus(extractId, volume, boxId)
                                }
                            }
                        } finally {
                            urlConnection.disconnect()
                        }
                    } else {
                        showToast("No more available injection labels")
                    }
                }



                // Usage
                CoroutineScope(Dispatchers.IO).launch {
                    // Assuming 'scanButtonSample.text' and 'scanButtonRack.text' are already defined
                    sendDataToDirectus(scanButtonExtract.text.toString(), inputNumber.toInt().toString(), scanButtonBox.text.toString())
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
                if (isBoxScanActive) {
                    scanButtonBox.text = result.contents // Update the button text
                    scanButtonExtract.visibility = View.VISIBLE // Show actionButton if valid
                } else if (isObjectScanActive) {
                    scanButtonExtract.text = result.contents // Update the button text
                    volumeInput.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun checkExistenceInDirectus(sampleId: String): String? {
        val accessToken = retrieveToken()
        for (i in 1..99) {
            val testId = "${sampleId}_${String.format("%02d", i)}"
            val url = URL("http://directus.dbgi.org/items/Aliquots?filter[aliquot_id][_eq]=$testId")

            val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

            try {
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    hasTriedAgain = false
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
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        showToast("connection to directus lost, reconnecting...")
                        // Retry the operation with the new access token
                        return checkExistenceInDirectus(sampleId)
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
                    showToast("Database error, please check your connection.")
                    deferred.complete(null)
                }
            }catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("Database error, please check your connection.")
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