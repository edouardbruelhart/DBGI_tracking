@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
    private lateinit var emptyPlace: TextView
    private lateinit var aliquotsMethodLabel: TextView
    private lateinit var scanButtonExtract: Button
    private var isBoxScanActive = false
    private var isObjectScanActive = false
    private lateinit var volumeInput: EditText
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
        emptyPlace = findViewById(R.id.emptyPlace)
        aliquotsMethodLabel = findViewById(R.id.aliquotsMethodLabel)
        scanButtonExtract = findViewById(R.id.scanButtonExtract)
        volumeInput = findViewById(R.id.volumeInput)

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
                    scanButtonBox.visibility = View.VISIBLE // Show actionButton if valid
                    scanBoxLabel.visibility = View.VISIBLE
                } else {
                    volumeInput.setBackgroundResource(android.R.color.holo_red_light) // Set background to red if not valid
                    scanButtonBox.visibility = View.INVISIBLE
                    scanBoxLabel.visibility = View.INVISIBLE
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (requestCode == IntentIntegrator.REQUEST_CODE) {
                    val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

                    // Initiate the activity when a QR is scanned
                    if (result != null && result.contents != null) {

                        if (isBoxScanActive) {
                            val box = result.contents
                            val boxValueExt = checkBoxLoadExt(box)
                            val boxValueAl = checkBoxLoadAl(box)
                            val boxValueBa = checkBoxLoadBa(box)
                            val stillPlace = 81 - boxValueExt - boxValueAl - boxValueBa
                            val boxValue = boxValueAl + boxValueExt + boxValueBa
                            if (boxValue >= 0 && stillPlace > 0) {
                                scanButtonBox.text = result.contents // Update the button text
                                scanButtonExtract.visibility = View.VISIBLE // Show actionButton if valid
                                volumeInput.visibility = View.INVISIBLE
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.setTextColor(Color.GRAY)
                                emptyPlace.text =
                                    "This box should still contain $stillPlace empty places"
                            } else {
                                handleInvalidScanResult(stillPlace, boxValue)
                                volumeInput.visibility = View.VISIBLE
                            }

                        } else if (isObjectScanActive) {
                            scanButtonExtract.text = result.contents // Update the button text
                            val inputNumber = volumeInput.text.toString()
                            // Usage
                            CoroutineScope(Dispatchers.IO).launch {
                                // Assuming 'scanButtonSample.text' and 'scanButtonRack.text' are already defined
                                if (scanButtonExtract.text.toString().matches(Regex("^dbgi_\\d{6}_\\d{2}\$"))) {
                                    sendDataToDirectus(
                                        scanButtonExtract.text.toString(),
                                        inputNumber.toInt().toString(),
                                        scanButtonBox.text.toString()
                                    )
                                } else if (scanButtonExtract.text.toString().matches(Regex("^dbgi_batch_blk_\\d{6}\$"))) {
                                    sendBlankToDirectus(
                                        scanButtonExtract.text.toString(),
                                        inputNumber.toInt().toString(),
                                        scanButtonBox.text.toString()
                                    )
                                }
                            }
                        }
                    }
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

    // Function to send data to Directus
    @SuppressLint("DiscouragedApi", "SetTextI18n")
    suspend fun sendDataToDirectus(extractId: String, volume: String, boxId: String) {

        // Define the table url
        val collectionUrl = "http://directus.dbgi.org/items/Aliquots"

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

                    // Check if there is still enough place in the rack before initiating the QR code reader
                    CoroutineScope(Dispatchers.IO).launch {
                        val box = scanButtonBox.text.toString()
                        val upBoxValueExt = checkBoxLoadExt(box)
                        val upBoxValueAl = checkBoxLoadAl(box)
                        val upBoxValueBa = checkBoxLoadBa(box)
                        val upStillPlace = 81 - upBoxValueExt - upBoxValueAl - upBoxValueBa

                        withContext(Dispatchers.Main) {

                            if(upStillPlace > 0){
                                // Automatically launch the QR scanning when last sample correctly added to the database
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.text = "This box should still contain $upStillPlace empty places"
                                delay(1500)
                                startQRScan("Scan object's QR")
                            } else {
                                emptyPlace.text = "Box is full, scan another one to continue"
                                scanButtonBox.text = "scan another box"
                                scanButtonExtract.text = "Begin to scan extracts"
                                scanButtonExtract.visibility = View.INVISIBLE

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
                        return sendDataToDirectus(extractId, volume, boxId)
                    }
                } else {
                    showToast("Database error, please try again")
                }
            } finally {
                urlConnection.disconnect()
            }
        } else {
            showToast("No more available injection labels")
        }
    }

    @SuppressLint("DiscouragedApi", "SetTextI18n")
    suspend fun sendBlankToDirectus(extractId: String, volume: String, boxId: String) {

        // Define the table url
        val collectionUrl = "http://directus.dbgi.org/items/Blank_Aliquots"

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
                    put("blk_id", extractId)
                    put("aliquot_volume_microliter", volume)
                    put("container_9x9_id", boxId)
                    put("status", "OK")
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
                        val selectedFileName = "template_dbgi_batch"

                        // Initialize an input stream by opening the specified file.
                        val iStream = resources.openRawResource(
                            resources.getIdentifier(
                                selectedFileName, "raw",
                                packageName
                            )
                        )
                        val parts = injectId.split("_")
                        val sample = "_" + parts[3]
                        val injection = "_" + parts[4]

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
                                "injection" -> {
                                    placeholder.value = injection
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

                    // Check if there is still enough place in the rack before initiating the QR code reader
                    CoroutineScope(Dispatchers.IO).launch {
                        val box = scanButtonBox.text.toString()
                        val upBoxValueExt = checkBoxLoadExt(box)
                        val upBoxValueAl = checkBoxLoadAl(box)
                        val upBoxValueBa = checkBoxLoadBa(box)
                        val upStillPlace = 81 - upBoxValueExt - upBoxValueAl - upBoxValueBa

                        withContext(Dispatchers.Main) {

                            if(upStillPlace > 0){
                                // Automatically launch the QR scanning when last sample correctly added to the database
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.text = "This box should still contain $upStillPlace empty places"
                                delay(1500)
                                startQRScan("Scan object's QR")
                            } else {
                                emptyPlace.text = "Box is full, scan another one to continue"
                                scanButtonBox.text = "scan another box"
                                scanButtonExtract.text = "Begin to scan extracts"
                                scanButtonExtract.visibility = View.INVISIBLE

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
                        return sendBlankToDirectus(extractId, volume, boxId)
                    }
                } else {
                    showToast("Database error, please try again")
                }
            } finally {
                urlConnection.disconnect()
            }
        } else {
            showToast("No more available injection labels")
        }
    }

    // Function to ask how many samples are already present in the rack to directus
    private suspend fun checkBoxLoadExt(boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url =
                URL("http://directus.dbgi.org/items/Lab_Extracts/?filter[container_9x9_id][_eq]=$boxId")
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
                        return@withContext checkBoxLoadExt(boxId)
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

    private suspend fun checkBoxLoadAl(boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url =
                URL("http://directus.dbgi.org/items/Aliquots/?filter[container_9x9_id][_eq]=$boxId")
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
                        return@withContext checkBoxLoadAl(boxId)
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

    private suspend fun checkBoxLoadBa(boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url = URL("http://directus.dbgi.org/items/Blank_Extracts/?filter[container_9x9_id][_eq]=$boxId")
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
                        return@withContext checkBoxLoadBa(boxId)
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

    // Manage errors information to guide the user
    @SuppressLint("SetTextI18n")
    private fun handleInvalidScanResult(stillPlace: Int, boxValue: Int) {
        emptyPlace.visibility = View.VISIBLE
        when {
            stillPlace < 1 -> {
                emptyPlace.text = "This box is full, please scan another one"
                scanButtonBox.text = "Value"
                scanButtonExtract.text = "Begin to scan samples"
            }
            boxValue < 0 -> {
                emptyPlace.text = "Database error, please check your connection."
            }
        }
        emptyPlace.setTextColor(Color.RED)
    }
}