@file:Suppress("ControlFlowWithEmptyBody")

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.bradysdk.api.printerconnection.PrinterDetails
import com.bradysdk.api.printerconnection.PrinterUpdateListener
import com.bradysdk.api.printerdiscovery.DiscoveredPrinterInformation
import com.bradysdk.api.printerdiscovery.PrinterDiscovery

@SuppressLint("StaticFieldLeak")
object PrinterDetailsSingleton : AppCompatActivity() {

    lateinit var printerDiscovery: PrinterDiscovery
    lateinit var printerDetails: PrinterDetails
    private var value: Int = -1

    private fun disconnectPreviousPrinter() {
        val lastConnectedPrinter = printerDiscovery.lastConnectedPrinter
        if (lastConnectedPrinter != null) {
            printerDiscovery.forgetLastConnectedPrinter()
        }
    }


    fun connectToPrinter(
        context: Context,
        printerSelected: DiscoveredPrinterInformation,
        pul: PrinterUpdateListener,
    ): Int {

        disconnectPreviousPrinter()

        val r = Runnable {
            try {

                this.printerDetails = printerDiscovery.connectToDiscoveredPrinter(
                    context,
                    printerSelected,
                    listOf(pul)
                )!!
            } catch (_: NullPointerException) {

            } catch (_: Exception) {
            }
        }
        val connectThread = Thread(r)
        connectThread.start()
        return value
    }

    fun sendPrintDiscovery() {}
}