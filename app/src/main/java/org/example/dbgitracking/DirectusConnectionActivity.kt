// Connection page of the application. Permits to extract the directus token to perform further actions
// and to be sure that user is connected to the database before adding some information's on the database.
// After connection performed and verified, user is redirected to permissions.

package org.example.dbgitracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

class DirectusConnectionActivity : AppCompatActivity() {

    // Initialize UI elements
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button


    @SuppressLint("SetTextI18n")
    // Function that is launched when activity is called
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the link with the corresponding xml
        setContentView(R.layout.activity_directus_connection)

        // Retrieve UI elements from xml to perform action on them
        editTextUsername = findViewById(R.id.usernameEditText)
        editTextPassword = findViewById(R.id.passwordEditText)
        buttonLogin = findViewById(R.id.loginButton)

        // Define actions that are performed when user click on login button
        buttonLogin.setOnClickListener {

            // Retrieve username and password entered by the user
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()

            // display a message to inform user that the connection is in progress
            showToast("Connecting...")

            // Start a coroutine to perform the connection to directus and retrieve access token to further operations in the app
            CoroutineScope(Dispatchers.IO).launch {
                try {
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

                        // launch permission activity to ask permissions and pass important variables to it
                        val intent = Intent(this@DirectusConnectionActivity, PermissionsActivity::class.java)
                        intent.putExtra("USERNAME", username)
                        intent.putExtra("PASSWORD", password)
                        intent.putExtra("ACCESS_TOKEN", accessToken)
                        startActivity(intent)

                        finish()

                    } else {
                        withContext(Dispatchers.Main) {
                            showToast("Connection error. Please check your credentials")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        showToast("Connection error. Please check you internet connection")
                    }
                }
            }
        }
    }

    // function that permits to easily display temporary messages
    private fun showToast(toast: String?) {
        runOnUiThread { Toast.makeText(this, toast, Toast.LENGTH_SHORT).show() }
    }

}
