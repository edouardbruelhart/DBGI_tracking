// Activity that waits for a successful printer connection before releasing the user in the homepage.
// No xml related file because nothing displayed

package org.example.dbgitracking

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bradysdk.api.printerdiscovery.PrinterDiscovery

class WaitConnectionActivity : AppCompatActivity() {
    private lateinit var printerDiscovery: PrinterDiscovery
    private lateinit var connectionLabel: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        printerDiscovery = PrinterDetailsSingleton.printerDiscovery

            // controls that the phone has printer's ownership
            if (printerDiscovery.haveOwnership != null) {
                showToast("Connected!")
                val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

                val intent = Intent(this, HomePageActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
                finish()
            // relaunch the activity until phone has printer's ownership
            } else {
                val accessToken = intent.getStringExtra("ACCESS_TOKEN")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")
                val isPrinterConnected = intent.getStringExtra("IS_PRINTER_CONNECTED")

                val intent = Intent(this, WaitConnectionActivity::class.java)
                intent.putExtra("ACCESS_TOKEN", accessToken)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                intent.putExtra("IS_PRINTER_CONNECTED", isPrinterConnected)
                startActivity(intent)
            }

    }

    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_SHORT).show() }
    }

}