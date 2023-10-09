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
    var value: Int = -1


    fun connectToPrinter(
        context: Context,
        printerSelected: DiscoveredPrinterInformation,
        pul: PrinterUpdateListener,
    ): Int {
        val r = Runnable {
            try {

                this.printerDetails = printerDiscovery.connectToDiscoveredPrinter(
                    context,
                    printerSelected,
                    listOf(pul)
                )!!
                println("printerDetails1: $this.printerDetails")
                this.value = -1

                if (printerDiscovery.haveOwnership != null) {
                    // Handle successful connection
                    println("Succeeded!")
                    this.value = 1
                } else {
                    println("failed...")
                    this.value = 0
                }
            } catch (ex: NullPointerException) {
                println("Error1: ${ex.message}")
                this.value = 0
            } catch (ex: Exception) {
                println("Error2: ${ex.message}")
                this.value = 0
            }
        }
        val connectThread = Thread(r)
        connectThread.start()
        return value

    }

    fun sendPrintDiscovery() {}
}
