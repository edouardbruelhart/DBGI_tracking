// Activity that permits to ask necessary permissions for the application. No UI for this one.

package org.example.dbgitracking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

@Suppress("DEPRECATION", "PrivatePropertyName")
class PermissionsActivity : AppCompatActivity() {

    // Initialize integers to control if bluetooth and location are activated
    private val RequestEnableBt: Int = 1
    private val RequestEnableLocation: Int = 2

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissions()

    }

    private fun permissions() {
        // Ask bluetooth, location and camera permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    @SuppressLint("InlinedApi")
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->

            val bluetoothCPermission = Manifest.permission.BLUETOOTH_CONNECT

            val permissionStatusBluetoothC =
                ActivityCompat.checkSelfPermission(this, bluetoothCPermission)

            val bluetoothSPermission = Manifest.permission.BLUETOOTH_SCAN

            val permissionStatusBluetoothS =
                ActivityCompat.checkSelfPermission(this, bluetoothSPermission)

            val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

            val permissionStatusLocation = ActivityCompat.checkSelfPermission(this, locationPermission)

            val cameraPermission = Manifest.permission.CAMERA
            val permissionStatusCamera = ActivityCompat.checkSelfPermission(this, cameraPermission)

            // check if all permissions are granted
            if (permissionStatusBluetoothC == PackageManager.PERMISSION_GRANTED
                && permissionStatusBluetoothS == PackageManager.PERMISSION_GRANTED
                && permissionStatusCamera == PackageManager.PERMISSION_GRANTED
                && permissionStatusLocation == PackageManager.PERMISSION_GRANTED
            ) {
                activation()

            // if camera not granted, asks the user again, otherwise application is useless.
            } else if (permissionStatusCamera != PackageManager.PERMISSION_GRANTED) {
                showToast("You need to accept camera permissions to use this app")

                val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")

                val intent = Intent(this, PermissionsActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                startActivity(intent)
                finish()

            // if not all bluetooth and location permissions are granted, the user won't be able to connect to a brady printer.
            // So it redirects him directly to homepage, without possibility to connect a printer. User can grant permissions
            // manually in application parameters to allow connection to printers.
            } else {
                showToast("You need to accept bluetooth and location permissions to use printers")

                val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val isPrinterConnected = "no"

                val intent = Intent(this, HomePageActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
                finish()
            }
        }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                activation()
            } else {
                showToast("You need to accept bluetooth permissions to use printers")

                val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val isPrinterConnected = "no"

                val intent = Intent(this, HomePageActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
                finish()
            }
        }

    // Controls if bluetooth and location are enable to perform a printer connection
    private fun activation() {
        val bluetoothSniffer = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothEnabled = bluetoothSniffer?.isEnabled ?: false

        val locationSniffer = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationSniffer.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isBluetoothEnabled || !isLocationEnabled) {
            showEnableBluetoothAndLocation()
        } else {
            // Both Bluetooth and Location are enabled,
            launchNextActivity()
        }
    }

    // If bluetooth and/or location are not enabled, ask the user if he wants to activate it.
    private fun showEnableBluetoothAndLocation() {
        val message = "Bluetooth and location need to be enabled to connect a printer."

        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Enable") { _, _ ->
                enableBluetoothAndLocation()
            }
            .setNegativeButton("Cancel") { _, _ ->
                launchNextActivity()
            }
            .setCancelable(false)
            .show()
    }

    // If he accepts to activate bluetooth and/or location, this function activates both.
    private fun enableBluetoothAndLocation() {
        val bluetoothSniffer = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothEnabled = bluetoothSniffer?.isEnabled ?: false

        val locationSniffer = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationSniffer.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isBluetoothEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            startActivityForResult(enableBtIntent, RequestEnableBt)
        }

        if (!isLocationEnabled) {
            val locationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(locationIntent, RequestEnableLocation)
        }

        if (isBluetoothEnabled && isLocationEnabled) {
            launchNextActivity()
        }
    }

    // launches the next activity
    private fun launchNextActivity() {
        // Both Bluetooth and Location are enabled
        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val username = intent.getStringExtra("USERNAME")
        val password = intent.getStringExtra("PASSWORD")

        val intent = Intent(this, PrinterConnectActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", accessToken)
        intent.putExtra("USERNAME", username)
        intent.putExtra("PASSWORD", password)
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RequestEnableBt && resultCode == RESULT_OK) {
            // Bluetooth was enabled
            activation() // Check Location again
        } else if (requestCode == RequestEnableLocation) {
            // Location settings changed, check both again
            activation()
        }
    }

    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_SHORT).show() }
    }

}