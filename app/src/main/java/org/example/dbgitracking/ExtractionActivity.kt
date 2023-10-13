@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
import com.bradysdk.api.printerconnection.CutOption
import com.bradysdk.api.printerconnection.PrintingOptions
import com.bradysdk.printengine.templateinterface.TemplateFactory
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CompletableDeferred
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

    private lateinit var buttonNewBatch: Button
    private lateinit var newExtractionMethod: TextView
    private lateinit var extractionMethodLabel: TextView
    private lateinit var extractionMethodSpinner: Spinner
    private lateinit var extractionMethodBatch: TextView
    private lateinit var extractionMethodBox: TextView
    private lateinit var extractionInformation: TextView
    private lateinit var scanButtonBatch: Button
    private lateinit var scanButtonBox: Button
    private lateinit var scanButtonSample: Button
    private lateinit var emptyPlace: TextView
    private var hasTriedAgain = false

    private var choices: List<String> = mutableListOf("Choose an option")

    private var isBatchActive = false
    private var isBoxScanActive = false
    private var isObjectScanActive = false

    @SuppressLint("CutPasteId", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extraction)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        buttonNewBatch = findViewById(R.id.buttonNewBatch)
        newExtractionMethod = findViewById(R.id.newExtractionMethod)
        extractionMethodLabel = findViewById(R.id.extractionMethodLabel)
        extractionMethodSpinner = findViewById(R.id.extractionMethodSpinner)
        extractionMethodBox = findViewById(R.id.extractionMethodBox)
        extractionMethodBatch = findViewById(R.id.extractionMethodBatch)
        extractionInformation = findViewById(R.id.extractionInformation)
        scanButtonBatch = findViewById(R.id.scanButtonBatch)
        scanButtonBox = findViewById(R.id.scanButtonBox)
        scanButtonSample = findViewById(R.id.scanButtonSample)
        emptyPlace = findViewById(R.id.emptyPlace)


        // Set up button to generate a new batch identifier
        buttonNewBatch.setOnClickListener {

        }

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
            isBatchActive = false
            startQRScan("Scan box's QR")
        }

        // Set up button click listener for Batch QR Scanner
        scanButtonBatch.setOnClickListener {
            isBatchActive = true
            isBoxScanActive = false
            isObjectScanActive = false
            startQRScan("Scan batch's QR")
        }

        // Set up button click listener for Object QR Scanner
        scanButtonSample.setOnClickListener {
            isObjectScanActive = true
            isBoxScanActive = false
            isBatchActive = false
            startQRScan("Scan object's QR")
        }
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

                        if (isBoxScanActive) {
                            val accessToken = intent.getStringExtra("ACCESS_TOKEN").toString()
                            val box = result.contents
                            val boxValueExt = checkBoxLoadExt(accessToken, box)
                            val boxValueAl = checkBoxLoadAl(accessToken, box)
                            val boxValueBa = checkBoxLoadBa(accessToken, box)
                            showToast("box value ba: $boxValueBa")
                            val stillPlace = 81 - boxValueExt - boxValueAl - boxValueBa
                            val boxValue = boxValueAl + boxValueExt + boxValueBa
                            if (boxValue >= 0 && stillPlace > 0) {
                                scanButtonBox.text = result.contents
                                extractionMethodBatch.visibility = View.VISIBLE
                                scanButtonBatch.visibility = View.VISIBLE
                                emptyPlace.visibility = View.VISIBLE
                                emptyPlace.setTextColor(Color.GRAY)
                                emptyPlace.text =
                                    "This box should still contain $stillPlace empty places"
                            } else {
                                handleInvalidScanResult(stillPlace, boxValue)
                            }
                        } else if (isBatchActive) {
                        val accessToken = intent.getStringExtra("ACCESS_TOKEN").toString()
                        val box = scanButtonBox.text.toString()
                        val baBoxValueExt = checkBoxLoadExt(accessToken, box)
                        val baBoxValueAl = checkBoxLoadAl(accessToken, box)
                        val baBoxValueBa = checkBoxLoadBa(accessToken, box)
                        val baStillPlace = 81 - baBoxValueExt - baBoxValueAl - baBoxValueBa
                        val boxValue = baBoxValueAl + baBoxValueExt + baBoxValueBa
                        if (boxValue >= 0 && baStillPlace > 0) {
                            scanButtonBatch.text = result.contents
                            scanButtonSample.visibility = View.VISIBLE
                            emptyPlace.visibility = View.VISIBLE
                            emptyPlace.setTextColor(Color.GRAY)
                            emptyPlace.text =
                                "This box should still contain $baStillPlace empty places"
                            } else {
                            handleInvalidScanResult(baStillPlace, boxValue)
                        }
                        } else if (isObjectScanActive) {
                            scanButtonSample.text = result.contents
                            val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                            val boxId = scanButtonBox.text.toString()
                            val sampleId = scanButtonSample.text.toString()
                            val selectedValue = extractionMethodSpinner.selectedItem.toString()
                            if (accessToken != null) {
                                withContext(Dispatchers.IO) {
                                    sendDataToDirectus(accessToken, sampleId, boxId, selectedValue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    // Function to send data to Directus
    @SuppressLint("SetTextI18n")
    private suspend fun sendDataToDirectus(accessToken: String, extractId: String, boxId: String, extractionMethod: String) {
        val parts = extractId.split("_")
        val withoutTemp = parts[0] + "_" + parts[1] + "_" + parts[2]
        // Define the table url
        val collectionUrl = "http://directus.dbgi.org/items/Lab_Extracts/$withoutTemp"
        val url = URL(collectionUrl)
        val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

        try {
            urlConnection.requestMethod = "PATCH"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")

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
                showToast("Database correctly updated")

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
                    val parts = withoutTemp.split("_")
                    val sample = "_" + parts[1]
                    val extract = "_" + parts[2]
                    val injetemp = ""

                    // Call the SDK method ".getTemplate()" to retrieve its Template Object
                    val template =
                        TemplateFactory.getTemplate(iStream, this@ExtractionActivity)
                    // Simple way to iterate through any placeholders to set desired values.
                    for (placeholder in template.templateData) {
                        when (placeholder.name) {
                            "QR" -> {
                                placeholder.value = withoutTemp
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
                    val accessToken = intent.getStringExtra("ACCESS_TOKEN").toString()
                    val box = scanButtonBox.text.toString()
                    val upBoxValueExt = checkBoxLoadExt(accessToken, box)
                    val upBoxValueAl = checkBoxLoadAl(accessToken, box)
                    val upBoxValueBa = checkBoxLoadBa(accessToken, box)
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
                            scanButtonSample.text = "Begin to scan samples"
                            scanButtonSample.visibility = View.INVISIBLE

                        }

                    }
                }
            } else if (!hasTriedAgain) {
                hasTriedAgain = true
                val newAccessToken = getNewAccessToken()
                if (newAccessToken != null) {
                    // Retry the operation with the new access token
                    return sendDataToDirectus(newAccessToken, scanButtonSample.text.toString(), scanButtonBox.text.toString(), extractionMethodSpinner.selectedItem.toString())
                }
            } else {
                showToast("Database error, please try again")
            }
        } finally {
            urlConnection.disconnect()
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchValuesAndPopulateSpinner() {
        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val apiUrl = "http://directus.dbgi.org/items/Extraction_Methods" // Replace with your collection URL
        val url = URL(apiUrl)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
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
                    val value = jsonObject.getString("method_name")
                    val description = jsonObject.getString("method_description")
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
    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_LONG).show() }
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

    // Function to ask how many samples are already present in the rack to directus
    private suspend fun checkBoxLoadExt(accessToken: String, boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val url = URL("http://directus.dbgi.org/items/Lab_Extracts/?filter[container_9x9_id][_eq]=$boxId")
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
                } else {
                    -1 // Return a value indicating an error
                }
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    private suspend fun checkBoxLoadAl(accessToken: String, boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val url = URL("http://directus.dbgi.org/items/Aliquots/?filter[container_9x9_id][_eq]=$boxId")
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
                } else {
                    -1 // Return a value indicating an error
                }
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    private suspend fun checkBoxLoadBa(accessToken: String, boxId: String): Int {

        return withContext(Dispatchers.IO) {
            val url = URL("http://directus.dbgi.org/items/Batch/?filter[container_9x9_id][_eq]=$boxId")
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
                } else {
                    -1 // Return a value indicating an error
                }
            } finally {
                urlConnection.disconnect()
            }
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
                scanButtonSample.text = "Begin to scan samples"
            }
            boxValue < 0 -> {
                emptyPlace.text = "Database error, please check your connection."
            }
        }
        emptyPlace.setTextColor(Color.RED)
    }

}
