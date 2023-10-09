package org.example.dbgitracking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bradysdk.api.printerconnection.PrinterProperties
import com.bradysdk.api.printerconnection.PrinterUpdateListener
import com.bradysdk.api.printerdiscovery.DiscoveredPrinterInformation
import com.bradysdk.api.printerdiscovery.PrinterDiscoveryListener
import com.bradysdk.printengine.printinginterface.PrinterDiscoveryFactory

class ManagePrinterActivity : AppCompatActivity(), PrinterUpdateListener, PrinterDiscoveryListener {

    private lateinit var choosePrinter: TextView
    private lateinit var printerListView: ListView
    private lateinit var adapter: PrinterListAdapter

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_printer)

        // Initialize the ListView
        printerListView = findViewById(R.id.printerListView)
        choosePrinter = findViewById(R.id.choosePrinter)

        permission()

        val printerList = ArrayList<DiscoveredPrinterInformation>()
        adapter = PrinterListAdapter(this, printerList)
        printerListView.adapter = adapter

        // Set an item click listener for the ListView
        printerListView.setOnItemClickListener { parent, view, position, id ->
            val selectedPrinter = adapter.getItem(position)
            // Pass it to connectToPrinter
            if (selectedPrinter != null) PrinterDetailsSingleton.connectToPrinter(
                this,
                selectedPrinter,
                this
            )
            showToast("Connecting...")
            setPrinterDetails()
            showToast("Connected")

        }

        val setup = setupPrinterDiscovery(this)
        if (setup == 1) {
            val lastConnectedPrinter = PrinterDetailsSingleton.printerDiscovery.lastConnectedPrinter
            PrinterDetailsSingleton.connectToPrinter(this, lastConnectedPrinter, this)
            showToast("Printer connected")
            setPrinterDetails()

        } else if (setup == 0){
            PrinterDetailsSingleton.printerDiscovery.startBlePrinterDiscovery()
            showToast("Select a printer...")
        } else if (setup == -1){
            showToast("Error, kill and relaunch the application")
        }

    }

    fun permission () {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    fun setupPrinterDiscovery(context: Context): Int {
        try {
            val printerDiscoveryListeners: MutableList<PrinterDiscoveryListener> = ArrayList()
            printerDiscoveryListeners.add(this)
            PrinterDetailsSingleton.printerDiscovery = PrinterDiscoveryFactory.getPrinterDiscovery(
                context.applicationContext,
                printerDiscoveryListeners
            )
            PrinterDetailsSingleton.sendPrintDiscovery()
            val lastConnectedPrinter = PrinterDetailsSingleton.printerDiscovery.lastConnectedPrinter
            if (lastConnectedPrinter != null && lastConnectedPrinter.name != "") {
                //connectToPrinter(lastConnectedPrinter, this, true)
                return 1
            } else {
                //printerDiscovery.startBlePrinterDiscovery()
                return 0
            }
        } catch (ex: Exception) {
            println("Error: ${ex.message}")
            return -1
        }
    }

    val isPrinterConnected = "yes"

    fun setPrinterDetails() {
        val access_token = intent.getStringExtra("ACCESS_TOKEN")
        val username = intent.getStringExtra("USERNAME")
        val password = intent.getStringExtra("PASSWORD")

        val intent = Intent(this, HomePageActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", access_token)
        intent.putExtra("USERNAME", username)
        intent.putExtra("PASSWORD", password)
        intent.putExtra("ISPRINTERCONNECTED", isPrinterConnected)
        startActivity(intent)
    }

    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_LONG).show() }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("test006", "${it.key} = ${it.value}")
            }
        }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(
                    this@ManagePrinterActivity,
                    "Permission ok",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@ManagePrinterActivity,
                    "Permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun printerDiscovered(p0: DiscoveredPrinterInformation?) {
        p0?.let {
            // Check if a printer with the same name already exists in the list
            adapter.clear()
            // Add the discovered printer to the list
            adapter.add(it)
            adapter.notifyDataSetChanged()
        }
    }

    override fun printerDiscoveryStarted() {
        // Implement the code for when printer discovery starts
        Log.d("PrinterConnectActivity", "Printer discovery started")
    }

    override fun printerDiscoveryStopped() {
        // Implement the code for when printer discovery stops
        Log.d("PrinterConnectActivity", "Printer discovery stopped")
    }

    class PrinterListAdapter(
        context: Context,
        printerList: ArrayList<DiscoveredPrinterInformation>
    ) : ArrayAdapter<DiscoveredPrinterInformation>(context, 0, printerList) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                R.layout.printer_list_item, parent, false
            )

            val printerInfo = getItem(position)

            // Set the printer information in the view
            val printerNameTextView = view.findViewById<TextView>(R.id.printerNameTextView)
            printerNameTextView.text = printerInfo?.name

            return view
        }
        fun onPrinterUpdate(printerProperties: MutableList<PrinterProperties>?) {
            // Implement the logic for handling printer update
        }
    }

    override fun PrinterUpdate(p0: MutableList<PrinterProperties>?) {
    }
}