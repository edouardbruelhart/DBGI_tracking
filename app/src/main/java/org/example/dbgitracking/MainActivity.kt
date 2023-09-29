// Connection page of the application. Permits to extract the directus token to perform further actions
// and to be sure that user is connected to the database before adding some informations on the database.
// After connection performed and verified, user is redirected to home page to select the action he wants to perform.

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var connectionStatusTextView: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTextUsername = findViewById(R.id.usernameEditText)
        editTextPassword = findViewById(R.id.passwordEditText)
        buttonLogin = findViewById(R.id.loginButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)

        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()

            connectionStatusTextView.text = "Connecting..."

            // Start a coroutine to perform the network operation
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val base_url = "http://directus.dbgi.org"
                    val login_url = "$base_url/auth/login"
                    val url = URL(login_url)
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

                        val access_token = data.getString("access_token")
                        //val refresh_token = data.getString("refresh_token")

                        // Pass access_token to HomePageActivity
                        val homeIntent = Intent(this@MainActivity, HomePageActivity::class.java)
                        homeIntent.putExtra("ACCESS_TOKEN", access_token)
                        startActivity(homeIntent)

                        finish()

                    } else {
                        withContext(Dispatchers.Main) {
                            connectionStatusTextView.text = "Error connecting. Please check your credentials and/or verify your connection"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        connectionStatusTextView.text = "Error connecting. Please check your credentials."
                    }
                }
            }
        }
    }
}
