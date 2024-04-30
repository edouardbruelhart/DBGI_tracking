// Aliquots management page of the application. Permits to scan an extract and to add it to the database.
// Then prints the corresponding label if a brady printer is connected.

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
import androidx.camera.view.PreviewView
import com.bradysdk.api.printerconnection.CutOption
import com.bradysdk.api.printerconnection.PrintingOptions
import com.bradysdk.printengine.templateinterface.TemplateFactory
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

    // Initialize views
    private lateinit var aliquotVolumeLabel: TextView
    private lateinit var aliquotVolume: EditText
    private lateinit var scanButtonBoxLabel: TextView
    private lateinit var scanButtonBox: Button
    private lateinit var boxEmptyPlace: TextView
    private lateinit var scanButtonAliquotLabel: TextView
    private lateinit var scanButtonAliquot: Button

    // Initialize QR reader views
    private lateinit var previewView: PreviewView
    private lateinit var flashlightButton: Button
    private lateinit var scanStatus: TextView

    // Define variables
    private var isBoxScanActive = false
    private var isObjectScanActive = false
    private var hasTriedAgain = false
    private var lastAccessToken: String? = null
    private var isQrScannerActive = false
    private var selectedFileName: String = ""
    private var readyToSend = true
    private var localBoxValue: Int = 81

    // Function that is launched on class creation
    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aliquots)

        title = "Aliquots mode"

        // Defines back arrow
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        aliquotVolumeLabel = findViewById(R.id.aliquotVolumeLabel)
        aliquotVolume = findViewById(R.id.aliquotVolume)
        scanButtonBoxLabel = findViewById(R.id.scanButtonBoxLabel)
        scanButtonBox = findViewById(R.id.scanButtonBox)
        boxEmptyPlace = findViewById(R.id.boxEmptyPlace)
        scanButtonAliquotLabel = findViewById(R.id.scanButtonAliquotLabel)
        scanButtonAliquot = findViewById(R.id.scanButtonAliquot)

        // Initialize QR reader views
        previewView = findViewById(R.id.previewView)
        flashlightButton = findViewById(R.id.flashlightButton)
        scanStatus = findViewById(R.id.scanStatus)

        // Retrieve directus token from connection event
        val token = intent.getStringExtra("ACCESS_TOKEN").toString()

        // stores the original token
        retrieveToken(token)

        // Add a TextWatcher to the numberInput for real-time validation
        aliquotVolume.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val inputText = s.toString()
                val inputNumber = inputText.toFloatOrNull()

                if (inputNumber != null) {
                    aliquotVolume.setBackgroundResource(android.R.color.transparent) // Set background to transparent if valid
                    scanButtonBox.visibility = View.VISIBLE // Show actionButton if valid
                    scanButtonBoxLabel.visibility = View.VISIBLE
                } else {
                    aliquotVolume.setBackgroundResource(android.R.color.holo_red_light) // Set background to red if not valid
                    scanButtonBox.visibility = View.INVISIBLE
                    scanButtonBoxLabel.visibility = View.INVISIBLE
                }
            }
        })

        // Set up button click listener for Box QR Scanner
        scanButtonBox.setOnClickListener {
            isBoxScanActive = true
            isObjectScanActive = false
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan box"
            flashlightButton.visibility = View.VISIBLE
            scanButtonBox.visibility = View.INVISIBLE
            scanButtonAliquotLabel.visibility = View.INVISIBLE
            scanButtonAliquot.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedBox ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                scanButtonBox.visibility = View.VISIBLE
                scanButtonAliquotLabel.visibility = View.VISIBLE
                scanButtonAliquot.visibility = View.VISIBLE
                scanStatus.text = ""
                scanButtonBox.text = scannedBox
                manageScan()
            }
        }

        // Set up button click listener for Object QR Scanner
        scanButtonAliquot.setOnClickListener {
            isBoxScanActive = false
            isObjectScanActive = true
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan vials"
            flashlightButton.visibility = View.VISIBLE
            scanButtonBox.visibility = View.INVISIBLE
            scanButtonAliquotLabel.visibility = View.INVISIBLE
            scanButtonAliquot.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedAliquot ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                scanButtonBox.visibility = View.VISIBLE
                scanButtonAliquotLabel.visibility = View.VISIBLE
                scanButtonAliquot.visibility = View.VISIBLE
                scanStatus.text = ""
                scanButtonAliquot.text = scannedAliquot
                manageScan()
            }
        }
    }

    // Store scanned values depending on which scan is performed
    @SuppressLint("SetTextI18n")
    @Deprecated("Deprecated in Java")
    fun manageScan() {

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {

                if (isBoxScanActive) {
                    val box = scanButtonBox.text.toString()
                    val boxValueExt = checkBoxLoadExt(box)
                    val boxValueAl = checkBoxLoadAl(box)
                    val parts = box.split("_")
                    val size = parts[1]
                    val sizeNumber = size.split("x")
                    val places = sizeNumber[0].toInt() * sizeNumber[1].toInt()
                    val stillPlace = places - boxValueExt - boxValueAl
                    val boxValue = boxValueAl + boxValueExt
                    localBoxValue = stillPlace
                    if (boxValue >= 0 && stillPlace > 0) {
                        scanButtonAliquotLabel.visibility = View.VISIBLE
                        scanButtonAliquot.visibility = View.VISIBLE
                        aliquotVolumeLabel.visibility = View.INVISIBLE
                        aliquotVolume.visibility = View.INVISIBLE
                        boxEmptyPlace.visibility = View.VISIBLE
                        boxEmptyPlace.setTextColor(Color.GRAY)
                        boxEmptyPlace.text =
                            "This box should still contain $stillPlace empty places"
                    } else {
                        handleInvalidScanResult(stillPlace, boxValue)
                        aliquotVolumeLabel.visibility = View.VISIBLE
                        aliquotVolume.visibility = View.VISIBLE
                    }

                } else if (isObjectScanActive) {
                    val inputNumber = aliquotVolume.text.toString()
                    // Usage
                    CoroutineScope(Dispatchers.IO).launch {
                        sendDataToDirectus(
                            scanButtonAliquot.text.toString(),
                            inputNumber.toInt().toString(),
                            scanButtonBox.text.toString()
                        )
                    }
                }
            }
        }
    }

    // Function to ask how many extracts are already present in this box
    private suspend fun checkBoxLoadExt(boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url =
                URL("http://directus.dbgi.org/items/Lab_Extracts/?filter[mobile_container_id][_eq]=$boxId")
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
                        return@withContext checkBoxLoadExt(boxId)
                    } else {
                        showToast("Connection error")
                        goToConnectionActivity()
                    }
                } else {
                    showToast("Connection error")
                    goToConnectionActivity()
                }
            } finally {
                urlConnection.disconnect()
            } as Int
        }
    }

    // Function to ask how many aliquots are already present in this box
    private suspend fun checkBoxLoadAl(boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            val url =
                URL("http://directus.dbgi.org/items/Aliquots/?filter[mobile_container_id][_eq]=$boxId")
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
                        return@withContext checkBoxLoadAl(boxId)
                    } else {
                        showToast("Connection error")
                        goToConnectionActivity()
                    }
                } else {
                    showToast("Connection error")
                    goToConnectionActivity()
                }
            } finally {
                urlConnection.disconnect()
            } as Int
        }
    }

    // Check which aliquot number to give to this sample.
    // Makes a request to directus and takes the biggest value,
    // then adds 1 to construct the actual code.
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

    // Function to send data to Directus
    @SuppressLint("DiscouragedApi", "SetTextI18n")
    suspend fun sendDataToDirectus(aliquotId: String, volume: String, boxId: String) {

        // Define the table url
        val collectionUrl = "http://directus.dbgi.org/items/Aliquots"

        val accessToken = retrieveToken()
        val url = URL(collectionUrl)

        val injectId = checkExistenceInDirectus(aliquotId)

        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

        runOnUiThread {
            if (isPrinterConnected == "yes") {
                readyToSend =
                    PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_Initialized" || PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
            }
        }

        if (injectId != null && readyToSend) {

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
                    put("lab_extract_id", aliquotId)
                    put("aliquot_volume_microliter", volume)
                    put("mobile_container_id", boxId)
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
                    if (isPrinterConnected == "yes") {
                        readyToSend =
                            PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_Initialized"
                                    || PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
                    }
                    if (isPrinterConnected == "yes" && readyToSend) {
                        val printerDetails = PrinterDetailsSingleton.printerDetails

                        if (printerDetails.printerModel == "M211") {
                            selectedFileName = if (scanButtonAliquot.text.toString().matches(Regex("^\\w{4}_\\d{6}_\\d{2}\$"))) {
                                "template_dbgi_m211"
                            } else {
                                "template_dbgi_batch_m211"
                            }

                        } else if (printerDetails.printerModel == "M511") {

                            selectedFileName = if (scanButtonAliquot.text.toString().matches(Regex("^\\w{4}_\\d{6}_\\d{2}\$"))
                            ) {
                                "template_dbgi_m511"
                            } else {
                                "template_dbgi_batch_m511"
                            }
                        }

                        // Initialize an input stream by opening the specified file.
                        val iStream = resources.openRawResource(
                            resources.getIdentifier(
                                selectedFileName, "raw",
                                packageName
                            )
                        )

                        if (scanButtonAliquot.text.toString().matches(Regex("^\\w{4}_\\d{6}_\\d{2}\$"))) {
                            val parts = injectId.split("_")
                            val code = parts[0]
                            val sample = "_" + parts[1]
                            val aliquot = "_" + parts[2]
                            val injetemp = "_" + parts[3]
                            // Call the SDK method ".getTemplate()" to retrieve its Template Object
                            val template =
                                TemplateFactory.getTemplate(iStream, this@AliquotsActivity)
                            // Simple way to iterate through any placeholders to set desired values.
                            for (placeholder in template.templateData) {
                                when (placeholder.name) {
                                    "dbgi" -> {
                                        placeholder.value = code
                                    }

                                    "QR" -> {
                                        placeholder.value = injectId
                                    }

                                    "sample" -> {
                                        placeholder.value = sample
                                    }

                                    "extract" -> {
                                        placeholder.value = aliquot
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
                        } else if (scanButtonAliquot.text.toString().matches(Regex("^dbgi_batch_blk_\\d{6}\$"))) {
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
                    } else {
                        showToast("Printer disconnected, data added to database.")
                    }

                    // Check if there is still enough place in the rack before initiating the QR code reader
                    CoroutineScope(Dispatchers.IO).launch {
                        localBoxValue--

                        withContext(Dispatchers.Main) {

                            if (localBoxValue > 0) {
                                // Automatically launch the QR scanning when last sample correctly added to the database
                                boxEmptyPlace.visibility = View.VISIBLE
                                boxEmptyPlace.text =
                                    "This box should still contain $localBoxValue empty places"
                                delay(1500)
                                scanButtonAliquot.performClick()
                            } else {
                                boxEmptyPlace.text = "Box is full, scan another one to continue"
                                scanButtonBox.text = "scan another box"
                                scanButtonAliquotLabel.visibility = View.INVISIBLE
                                scanButtonAliquot.text = "Value"
                                scanButtonAliquot.visibility = View.INVISIBLE

                            }

                        }
                    }
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        // Retry the operation with the new access token
                        return sendDataToDirectus(aliquotId, volume, boxId)
                    } else {
                        showToast("Connection error")
                        goToConnectionActivity()
                    }
                } else {
                    showToast("database error, the sample seems to be absent from the database.")
                }
            } finally {
                urlConnection.disconnect()
            }
        } else if (injectId == null) {
            showToast("No more available injection labels")
        } else {
            showToast("Printer disconnected, please reconnect it and scan the label again")
            goToPrinterConnectionActivity()
        }
    }

    // Manage errors information to guide the user
    @SuppressLint("SetTextI18n")
    private fun handleInvalidScanResult(stillPlace: Int, boxValue: Int) {
        boxEmptyPlace.visibility = View.VISIBLE
        when {
            stillPlace < 1 -> {
                boxEmptyPlace.text = "This box is full, please scan another one"
                scanButtonBox.text = "Value"
                scanButtonAliquot.text = "Begin to scan samples"
            }
            boxValue < 0 -> {
                showToast("Connection error")
                goToConnectionActivity()
            }
        }
        boxEmptyPlace.setTextColor(Color.RED)
    }

    // Performs a new connection to directus if the access token is no more valid.
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

    // Function to store always the last generated token.
    private fun retrieveToken(token: String? = null): String {
        if (token != null) {
            lastAccessToken = token
        }
        return lastAccessToken ?: "null"
    }

    // Redirects user to connection activity in case of connection loss.
    private fun goToConnectionActivity(){
        val intent = Intent(this, DirectusConnectionActivity::class.java)
        startActivity(intent)
    }

    // Redirects user to printer connection activity in case of printer connection loss.
    private fun goToPrinterConnectionActivity(){

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val username = intent.getStringExtra("USERNAME")
        val password = intent.getStringExtra("PASSWORD")
        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

        val intent = Intent(this,ManagePrinterActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", accessToken)
        intent.putExtra("USERNAME", username)
        intent.putExtra("PASSWORD", password)
        intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
        startActivity(intent)
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
                    scanButtonBoxLabel.visibility = View.VISIBLE
                    scanButtonBox.visibility = View.VISIBLE
                    scanStatus.text = ""
                    if (isObjectScanActive){
                        scanButtonAliquotLabel.visibility = View.VISIBLE
                        scanButtonAliquot.visibility = View.VISIBLE
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

    // function to facilitate toasts generation.
    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_LONG).show() }
    }
}