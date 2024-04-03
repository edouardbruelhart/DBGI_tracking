@file:Suppress("DEPRECATION")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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

@Suppress("NAME_SHADOWING")
class ExtractionActivity : AppCompatActivity() {

    private lateinit var newBatchButton: Button
    private lateinit var extractionMethodLabel: TextView
    private lateinit var newExtractionMethodLabel: TextView
    private lateinit var extractionMethodSpinner: Spinner
    private lateinit var extractionMethodDescription: TextView
    private lateinit var solventVolumeLabel: TextView
    private lateinit var solventVolume: EditText
    private lateinit var scanButtonBoxLabel: TextView
    private lateinit var scanButtonBox: Button
    private lateinit var boxEmptyPlace: TextView
    private lateinit var scanButtonBatchLabel: TextView
    private lateinit var scanButtonBatch: Button
    private lateinit var scanButtonExtractLabel: TextView
    private lateinit var scanButtonExtract: Button

    private lateinit var previewView: PreviewView
    private lateinit var flashlightButton: Button
    private lateinit var scanStatus: TextView

    private var choices: List<String> = mutableListOf("Choose an option")
    private var isBatchActive = false
    private var isBoxScanActive = false
    private var isObjectScanActive = false
    private var hasTriedAgain = false
    private var lastAccessToken: String? = null
    private var isQrScannerActive = false
    private var selectedFileName: String = ""
    private var readyToSend = true
    private var localBoxValue: Int = 81

    @SuppressLint("CutPasteId", "MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extraction)

        title = "Extraction mode"

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)

        // Initialize views
        newBatchButton = findViewById(R.id.newBatchButton)
        extractionMethodLabel = findViewById(R.id.extractionMethodLabel)
        newExtractionMethodLabel = findViewById(R.id.newExtractionMethodLabel)
        extractionMethodSpinner = findViewById(R.id.extractionMethodSpinner)
        extractionMethodDescription = findViewById(R.id.extractionMethodDescription)
        solventVolumeLabel = findViewById(R.id.solventVolumeLabel)
        solventVolume = findViewById(R.id.solventVolume)
        scanButtonBoxLabel = findViewById(R.id.scanButtonBoxLabel)
        scanButtonBox = findViewById(R.id.scanButtonBox)
        boxEmptyPlace = findViewById(R.id.boxEmptyPlace)
        scanButtonBatchLabel = findViewById(R.id.scanButtonBatchLabel)
        scanButtonBatch = findViewById(R.id.scanButtonBatch)
        scanButtonExtractLabel = findViewById(R.id.scanButtonExtractLabel)
        scanButtonExtract = findViewById(R.id.scanButtonExtract)

        previewView = findViewById(R.id.previewView)
        flashlightButton = findViewById(R.id.flashlightButton)
        scanStatus = findViewById(R.id.scanStatus)

        val token = intent.getStringExtra("ACCESS_TOKEN").toString()

        // stores the original token
        retrieveToken(token)

        solventVolume.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val inputText = s.toString()
                val inputVolume = inputText.toInt()

                if (inputVolume > 0) {
                    scanButtonBoxLabel.visibility = View.VISIBLE
                    scanButtonBox.visibility = View.VISIBLE
                } else {
                    scanButtonBoxLabel.visibility = View.INVISIBLE
                    scanButtonBox.visibility = View.INVISIBLE
                }
            }
        })

        // Set up button to generate a new batch identifier
        newBatchButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                generateNewBatch()
            }
        }

        // Make the link clickable
        val linkTextView: TextView = newExtractionMethodLabel
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
                    solventVolumeLabel.visibility = View.VISIBLE
                    solventVolume.visibility = View.VISIBLE
                } else {
                    solventVolumeLabel.visibility = View.INVISIBLE
                    solventVolume.visibility = View.INVISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed
            }
        }

        // Set up button click listener for Box QR Scanner
        scanButtonBox.setOnClickListener {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(solventVolume.windowToken, 0)
            isBoxScanActive = true
            isObjectScanActive = false
            isBatchActive = false
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan box"
            flashlightButton.visibility = View.VISIBLE
            newBatchButton.visibility = View.INVISIBLE
            newExtractionMethodLabel.visibility = View.INVISIBLE
            extractionMethodSpinner.visibility = View.INVISIBLE
            solventVolumeLabel.visibility = View.INVISIBLE
            solventVolume.visibility = View.INVISIBLE
            extractionMethodLabel.visibility = View.INVISIBLE
            scanButtonBox.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedBox ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                newBatchButton.visibility = View.VISIBLE
                newExtractionMethodLabel.visibility = View.VISIBLE
                extractionMethodSpinner.visibility = View.VISIBLE
                solventVolumeLabel.visibility = View.VISIBLE
                solventVolume.visibility = View.VISIBLE
                extractionMethodLabel.visibility = View.VISIBLE
                scanButtonBox.visibility = View.VISIBLE
                scanButtonBox.text = scannedBox
                scanStatus.text = ""
                if (scanButtonExtract.text != "Value"){
                    scanButtonExtractLabel.visibility = View.VISIBLE
                    scanButtonExtract.visibility = View.VISIBLE
                }
                manageScan()
            }
        }

        // Set up button click listener for Batch QR Scanner
        scanButtonBatch.setOnClickListener {
            isBatchActive = true
            isBoxScanActive = false
            isObjectScanActive = false
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan batch"
            flashlightButton.visibility = View.VISIBLE
            newBatchButton.visibility = View.INVISIBLE
            newExtractionMethodLabel.visibility = View.INVISIBLE
            extractionMethodSpinner.visibility = View.INVISIBLE
            solventVolumeLabel.visibility = View.INVISIBLE
            solventVolume.visibility = View.INVISIBLE
            extractionMethodLabel.visibility = View.INVISIBLE
            scanButtonBox.visibility = View.INVISIBLE
            scanButtonBatch.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedBatch ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                newBatchButton.visibility = View.VISIBLE
                newExtractionMethodLabel.visibility = View.VISIBLE
                extractionMethodSpinner.visibility = View.VISIBLE
                solventVolumeLabel.visibility = View.VISIBLE
                solventVolume.visibility = View.VISIBLE
                extractionMethodLabel.visibility = View.VISIBLE
                scanButtonBox.visibility = View.VISIBLE
                scanButtonBatch.visibility = View.VISIBLE
                scanButtonBatch.text = scannedBatch
                scanStatus.text = ""
                manageScan()
            }
        }

        // Set up button click listener for Object QR Scanner
        scanButtonExtract.setOnClickListener {
            isObjectScanActive = true
            isBoxScanActive = false
            isBatchActive = false
            isQrScannerActive = true
            previewView.visibility = View.VISIBLE
            scanStatus.text = "Scan extract"
            flashlightButton.visibility = View.VISIBLE
            newBatchButton.visibility = View.INVISIBLE
            newExtractionMethodLabel.visibility = View.INVISIBLE
            extractionMethodSpinner.visibility = View.INVISIBLE
            solventVolumeLabel.visibility = View.INVISIBLE
            solventVolume.visibility = View.INVISIBLE
            extractionMethodLabel.visibility = View.INVISIBLE
            scanButtonBox.visibility = View.INVISIBLE
            scanButtonBatch.visibility = View.INVISIBLE
            scanButtonExtractLabel.visibility = View.INVISIBLE
            scanButtonExtract.visibility = View.INVISIBLE
            QRCodeScannerUtility.initialize(this, previewView, flashlightButton) { scannedSample ->

                // Stop the scanning process after receiving the result
                QRCodeScannerUtility.stopScanning()
                isQrScannerActive = false
                previewView.visibility = View.INVISIBLE
                flashlightButton.visibility = View.INVISIBLE
                newBatchButton.visibility = View.VISIBLE
                newExtractionMethodLabel.visibility = View.VISIBLE
                extractionMethodSpinner.visibility = View.VISIBLE
                solventVolumeLabel.visibility = View.VISIBLE
                solventVolume.visibility = View.VISIBLE
                extractionMethodLabel.visibility = View.VISIBLE
                scanButtonBox.visibility = View.VISIBLE
                scanButtonBatch.visibility = View.VISIBLE
                scanButtonExtractLabel.visibility = View.VISIBLE
                scanButtonExtract.visibility = View.VISIBLE
                scanButtonExtract.text = scannedSample
                scanStatus.text = ""
                manageScan()
            }
        }
    }

    @SuppressLint("SetTextI18n", "SuspiciousIndentation")
    @Deprecated("Deprecated in Java")
    fun manageScan() {
        // Counts the spaces left in the rack
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
                    showToast("places: $localBoxValue")
                    if (boxValue >= 0 && stillPlace > 0) {
                        scanButtonBatchLabel.visibility = View.VISIBLE
                        scanButtonBatch.visibility = View.VISIBLE
                        boxEmptyPlace.visibility = View.VISIBLE
                        boxEmptyPlace.setTextColor(Color.GRAY)
                        boxEmptyPlace.text =
                                    "This box should still contain $stillPlace empty places"
                    } else {
                        handleInvalidScanResult(stillPlace, boxValue)
                    }
                } else if (isBatchActive) {
                    val box = scanButtonBox.text.toString()
                    val batch = scanButtonBatch.text.toString()
                    sendBatchToDirectus(batch, box)
                    val baBoxValueExt = checkBoxLoadExt(box)
                    val baBoxValueAl = checkBoxLoadAl(box)
                    val parts = box.split("_")
                    val size = parts[1]
                    val sizeNumber = size.split("x")
                    val places = sizeNumber[0].toInt() * sizeNumber[1].toInt()
                    val baStillPlace = places - baBoxValueExt - baBoxValueAl
                    val boxValue = baBoxValueAl + baBoxValueExt
                    if (boxValue >= 0 && baStillPlace > 0) {
                        scanButtonExtractLabel.visibility = View.VISIBLE
                        scanButtonExtract.visibility = View.VISIBLE
                        boxEmptyPlace.visibility = View.VISIBLE
                        boxEmptyPlace.setTextColor(Color.GRAY)
                        boxEmptyPlace.text =
                                "This box should still contain $baStillPlace empty places"
                    } else {
                            handleInvalidScanResult(baStillPlace, boxValue)
                    }
                } else if (isObjectScanActive) {
                    val boxId = scanButtonBox.text.toString()
                    val sampleId = scanButtonExtract.text.toString()
                    val selectedValue = extractionMethodSpinner.selectedItem.toString()
                    val batchSample = scanButtonBatch.text.toString()
                    val parts = batchSample.split("_")
                    val batchId = parts[0] + "_" + parts[1] + "_" + parts[3]
                    val inputVolume = solventVolume.text.toString()
                    withContext(Dispatchers.IO) {
                        sendDataToDirectus(sampleId, boxId, selectedValue, batchId, inputVolume)
                    }
                }
            }
        }
    }


    // Function to send data to Directus
    @SuppressLint("SetTextI18n", "DiscouragedApi")
    private suspend fun sendDataToDirectus(extractId: String, boxId: String, extractionMethod: String, batchId: String, inputVolume: String) {
        val parts = extractId.split("_")
        val withoutTemp = parts[0] + "_" + parts[1] + "_" + parts[2]
        // Define the table url
        val accessToken = retrieveToken()
        val collectionUrl = "http://directus.dbgi.org/items/Lab_Extracts/$withoutTemp"
        val url = URL(collectionUrl)
        val urlConnection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

        if (isPrinterConnected == "yes") {
            readyToSend =
                PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_Initialized"
                        || PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
            if (PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow") {
                showToast("Printer battery is low, please charge it")
            }
        }

        if (readyToSend) {

            try {
                urlConnection.requestMethod = "PATCH"
                urlConnection.setRequestProperty("Content-Type", "application/json")
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")

                val data = JSONObject().apply {
                    put("mobile_container_id", boxId)
                    put("extraction_method", extractionMethod)
                    put("batch_id", batchId)
                    put("solvent_volume_micro", inputVolume)
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
                    showToast("$extractId correctly updated")

                    // print label here
                    val printerDetails = PrinterDetailsSingleton.printerDetails
                    if (isPrinterConnected == "yes") {
                        readyToSend =
                            printerDetails.printerStatusMessage == "PrinterStatus_Initialized"
                                    || printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
                    }
                    if (isPrinterConnected == "yes" && readyToSend) {
                        if (printerDetails.printerModel == "M211") {
                            selectedFileName = "template_dbgi_m211"
                        } else if (printerDetails.printerModel == "M511") {
                            selectedFileName = "template_dbgi_m511"
                        }

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
                        val injetemp = " "

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
                    } else {
                        showToast("Printer disconnected, data added to database.")
                    }

                    // Check if there is still enough place in the rack before initiating the QR code reader
                    CoroutineScope(Dispatchers.IO).launch {
                        localBoxValue--
                        showToast("places: $localBoxValue")

                        withContext(Dispatchers.Main) {

                            if (localBoxValue > 0) {
                                // Automatically launch the QR scanning when last sample correctly added to the database
                                boxEmptyPlace.visibility = View.VISIBLE
                                boxEmptyPlace.text =
                                    "This box should still contain $localBoxValue empty places"
                                delay(1500)
                                scanButtonExtract.performClick()
                            } else {
                                boxEmptyPlace.text = "Box is full, scan another one to continue"
                                scanButtonBox.text = "scan another box"
                                scanButtonExtract.text = "Value"
                                scanButtonExtractLabel.visibility = View.INVISIBLE
                                scanButtonExtract.visibility = View.INVISIBLE
                            }
                        }
                    }
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()
                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        val batchSample = scanButtonBatch.text.toString()
                        val parts = batchSample.split("_")
                        val batchId = parts[0] + "_" + parts[1] + "_" + parts[3]
                        // Retry the operation with the new access token
                        return sendDataToDirectus(
                            extractId,
                            boxId,
                            extractionMethod,
                            batchId,
                            inputVolume
                        )
                    }
                } else {
                    showToast("database error, the sample seems to be absent from the database.")
                }
            } finally {
                urlConnection.disconnect()
            }
        } else {
            showToast("Printer disconnected, please reconnect it and scan the label again")
            goToPrinterConnectionActivity()
        }
    }


    // Function to send new batches to Directus
    @SuppressLint("SetTextI18n", "DiscouragedApi")
    private suspend fun generateNewBatch() {
        val accessToken = retrieveToken()
        // Define the table url
        val collectionUrl = "http://directus.dbgi.org/items/Batch/"

        val url = URL(collectionUrl)
        val urlConnection2 =
            withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

        val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

        if (isPrinterConnected == "yes") {
            readyToSend =
                PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_Initialized"
                        || PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
            if (PrinterDetailsSingleton.printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow") {
                showToast("Printer battery is low, please charge it")
            }
        }

        if (readyToSend) {

            try {
                val sortParam = "sort=-batch_id"
                val urlWithSort = URL("$collectionUrl?$sortParam")
                val urlConnection =
                    withContext(Dispatchers.IO) { urlWithSort.openConnection() as HttpURLConnection }

                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")

                val responseCode = urlConnection.responseCode
                showToast("response: $responseCode")
                showToast("batch id: ")
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

                    val jsonData = response.toString()
                    val jsonResponse = JSONObject(jsonData)
                    val lastValue =
                        jsonResponse.getJSONArray("data").getJSONObject(0).getString("batch_id")
                    val lastNumber = lastValue.split("_")[2].toInt()

                    // Define the first number of the list (last number + 1)
                    val firstNumber = lastNumber + 1

                    // Create a list with the asked codes beginning with the first number
                    val batchId = String.format("dbgi_batch_%06d", firstNumber)
                    urlConnection.disconnect()
                    urlConnection2.requestMethod = "POST"
                    urlConnection2.setRequestProperty("Content-Type", "application/json")
                    urlConnection2.setRequestProperty("Authorization", "Bearer $accessToken")

                    val data = JSONObject().apply {
                        put("Reserved", "True")
                        put("batch_id", batchId)
                    }

                    val outputStream: OutputStream = urlConnection2.outputStream
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

                    val responseCode = urlConnection2.responseCode

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
                        showToast("$batchId correctly added to database")

                        // print label here
                        val printerDetails = PrinterDetailsSingleton.printerDetails
                        if (isPrinterConnected == "yes") {
                            readyToSend =
                                printerDetails.printerStatusMessage == "PrinterStatus_Initialized"
                                        || printerDetails.printerStatusMessage == "PrinterStatus_BatteryLow"
                        }
                        if (isPrinterConnected == "yes" && readyToSend) {

                            if (printerDetails.printerModel == "M211") {
                                selectedFileName = "template_dbgi_batch_m211"
                            } else if (printerDetails.printerModel == "M511") {
                                selectedFileName = "template_dbgi_batch_m511"
                            }

                            // Initialize an input stream by opening the specified file.
                            val iStream = resources.openRawResource(
                                resources.getIdentifier(
                                    selectedFileName, "raw",
                                    packageName
                                )
                            )
                            val parts = batchId.split("_")
                            val batchSample =
                                parts[0] + "_" + parts[1] + "_" + "blk" + "_" + parts[2]
                            val sample = "_" + parts[2]

                            // Call the SDK method ".getTemplate()" to retrieve its Template Object
                            val template =
                                TemplateFactory.getTemplate(iStream, this)
                            // Simple way to iterate through any placeholders to set desired values.
                            for (placeholder in template.templateData) {
                                when (placeholder.name) {
                                    "QR" -> {
                                        placeholder.value = batchSample
                                    }

                                    "sample" -> {
                                        placeholder.value = sample
                                    }

                                    "injection" -> {
                                        placeholder.value = " "
                                    }
                                }
                            }

                            val printingOptions = PrintingOptions()
                            printingOptions.cutOption = CutOption.EndOfLabel
                            printingOptions.numberOfCopies = 2
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
                        } else {
                            showToast("Printer disconnected, data added to database.")
                        }
                    } else if (!hasTriedAgain) {
                        hasTriedAgain = true
                        val newAccessToken = getNewAccessToken()
                        if (newAccessToken != null) {
                            retrieveToken(newAccessToken)
                            // Retry the operation with the new access token
                            return generateNewBatch()
                        } else {
                            showToast("Connection error")
                            goToConnectionActivity()
                        }
                    } else {
                        showToast("Please scan a valid extraction blank")
                    }
                }
            } finally {
                urlConnection2.disconnect()
            }
        } else {
            showToast("Printer disconnected, please reconnect it and scan the label again")
            goToPrinterConnectionActivity()
        }
    }


    private fun fetchValuesAndPopulateSpinner() {
        val accessToken = retrieveToken()
        val apiUrl =
            "http://directus.dbgi.org/items/Extraction_Methods" // Replace with your collection URL
        val url = URL(apiUrl)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlConnection =
                    withContext(Dispatchers.IO) {
                        url.openConnection()
                    } as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
                withContext(Dispatchers.IO) {
                    urlConnection.connect()
                }

                val inputStream = urlConnection.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
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

                val responseCode = urlConnection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    hasTriedAgain = false
                    // Parse JSON response
                    val jsonArray = JSONObject(response.toString()).getJSONArray("data")
                    val values = ArrayList<String>()
                    val descriptions = HashMap<String, String>()

                    // Add "Choose an option" to the list of values
                    values.add("Choose a method")

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
                        val adapter = ArrayAdapter(
                            this@ExtractionActivity,
                            android.R.layout.simple_spinner_item,
                            values
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        extractionMethodSpinner.adapter = adapter

                        // Add an OnItemSelectedListener to update newExtractionMethod text and handle visibility
                        extractionMethodSpinner.onItemSelectedListener =
                            object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    parent: AdapterView<*>?,
                                    view: View?,
                                    position: Int,
                                    id: Long
                                ) {
                                    if (position > 0) { // Check if a valid option (not "Choose an option") is selected
                                        val selectedValue = values[position]
                                        val selectedDescription = descriptions[selectedValue]
                                        extractionMethodDescription.visibility = View.VISIBLE
                                        extractionMethodDescription.text = selectedDescription
                                        solventVolumeLabel.visibility = View.VISIBLE
                                        solventVolume.visibility = View.VISIBLE
                                    } else {
                                        extractionMethodDescription.visibility = View.INVISIBLE
                                        solventVolumeLabel.visibility = View.INVISIBLE
                                        solventVolume.visibility = View.INVISIBLE
                                    }
                                }

                                override fun onNothingSelected(parent: AdapterView<*>?) {
                                    //newExtractionMethod.text = "No suitable referenced method? add it by following this link and restart the application"
                                    solventVolumeLabel.visibility = View.INVISIBLE
                                    solventVolume.visibility = View.INVISIBLE
                                }
                            }
                    }
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()

                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        // Retry the operation with the new access token
                        return@launch fetchValuesAndPopulateSpinner()
                    } else {
                        showToast("Connection error")
                        goToConnectionActivity()
                    }
                } else {
                    showToast("Connection error")
                    goToConnectionActivity()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showToast("$e")
                goToConnectionActivity()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                return if (isQrScannerActive){
                    QRCodeScannerUtility.stopScanning()
                    isQrScannerActive = false
                    previewView.visibility = View.INVISIBLE
                    flashlightButton.visibility = View.INVISIBLE
                    newBatchButton.visibility = View.VISIBLE
                    newExtractionMethodLabel.visibility = View.VISIBLE
                    extractionMethodSpinner.visibility = View.VISIBLE
                    solventVolumeLabel.visibility = View.VISIBLE
                    solventVolume.visibility = View.VISIBLE
                    extractionMethodLabel.visibility = View.VISIBLE
                    scanButtonBox.visibility = View.VISIBLE
                    scanStatus.text = ""
                    if (isObjectScanActive){
                        scanButtonExtractLabel.visibility = View.VISIBLE
                        scanButtonExtract.visibility = View.VISIBLE
                        scanButtonBatch.visibility = View.VISIBLE
                    } else if (isBatchActive){
                        scanButtonBatch.visibility = View.VISIBLE
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
    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_SHORT).show() }
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
                    showToast("Connection error")
                    goToConnectionActivity()
                    deferred.complete(null)
                }
            }catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("$e")
                    goToConnectionActivity()
                    deferred.complete(null)
                }
            }
        }
        return deferred.await()
    }

    // Function to ask how many samples are already present in the rack to directus
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

    // Manage errors information to guide the user
    @SuppressLint("SetTextI18n")
    private fun handleInvalidScanResult(stillPlace: Int, boxValue: Int) {
        boxEmptyPlace.visibility = View.VISIBLE
        when {
            stillPlace < 1 -> {
                boxEmptyPlace.text = "This box is full, please scan another one"
                scanButtonBox.text = "Value"
                scanButtonExtract.text = "Value"
            }
            boxValue < 0 -> {
                showToast("Connection error")
                goToConnectionActivity()
            }
        }
        boxEmptyPlace.setTextColor(Color.RED)
    }

    @SuppressLint("SetTextI18n")
    private suspend fun sendBatchToDirectus(batchSample: String, boxId: String) {
        val parts = batchSample.split("_")
        val batch = parts[0] + "_" + parts[1] + "_" + parts[3]
        val selectedValue = extractionMethodSpinner.selectedItem.toString()
        withContext(Dispatchers.IO) {
            val accessToken = retrieveToken()
            // Define the table url
            val collectionUrl = "http://directus.dbgi.org/items/Lab_Extracts"
            val url = URL(collectionUrl)
            val urlConnection =
                withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }

            try {
                urlConnection.requestMethod = "POST"
                urlConnection.setRequestProperty("Content-Type", "application/json")
                urlConnection.setRequestProperty("Authorization", "Bearer $accessToken")
                val data = JSONObject().apply {
                    put("lab_extract_id", batchSample)
                    put("field_sample_id", "blank")
                    put("mobile_container_id", boxId)
                    put("extraction_method", selectedValue)
                    put("status", "OK")
                    put("solvent_volume_micro", solventVolume.text.toString())
                    put("batch_id", batch)
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
                    showToast("$batch correctly added to $boxId box")
                } else if (!hasTriedAgain) {
                    hasTriedAgain = true
                    val newAccessToken = getNewAccessToken()
                    if (newAccessToken != null) {
                        retrieveToken(newAccessToken)
                        // Retry the operation with the new access token
                        return@withContext sendBatchToDirectus(batchSample, boxId)
                    }
                } else {
                    showToast("batch already added to database")
                    scanButtonExtractLabel.visibility = View.INVISIBLE
                    scanButtonExtract.visibility = View.INVISIBLE
                }
            } finally {
                urlConnection.disconnect()
            }
        }
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
}
