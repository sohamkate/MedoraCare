
package com.soham.medicinereminderapp

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingIndicator: ProgressBar

    private val networkExecutor = Executors.newSingleThreadExecutor()

    private val workerUrl =
        "https://medora-ai.sohamkate209.workers.dev"

    private var requestInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        setContentView(R.layout.activity_ai_assistant)

        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        messageInput = findViewById(R.id.etMessage)
        sendButton = findViewById(R.id.btnSend)
        loadingIndicator = findViewById(R.id.progressAi)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        addMessage(
            "Medora AI",
            "Hello! I can help with general medicine reminders " +
                    "and wellness questions. I cannot diagnose " +
                    "conditions or decide medication doses."
        )

        sendButton.setOnClickListener {
            sendMessage()
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Handle system bars and the on-screen keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.aiRoot)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )

            insets
        }

        // Keep the latest conversation content visible.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.aiRoot)
        ) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )

            chatScrollView.post {
                scrollChatToBottom()
            }

            insets
        }
    }

    private fun sendMessage() {
        if (requestInProgress) return

        val message = messageInput.text.toString().trim()

        if (message.isEmpty()) {
            messageInput.error = "Enter a message"
            return
        }

        if (message.length > 2000) {
            messageInput.error =
                "Please keep your message under 2000 characters"
            return
        }

        addMessage("You", message)
        messageInput.text.clear()

        requestInProgress = true
        setLoading(true)

        networkExecutor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(workerUrl).openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                val requestJson = JSONObject()
                    .put("message", message)
                    .toString()

                connection.outputStream.use { output ->
                    output.write(
                        requestJson.toByteArray(Charsets.UTF_8)
                    )
                }

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val body = stream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

                val json = JSONObject(body)

                if (status in 200..299) {
                    val response = json.optString("response")

                    runOnUiThread {
                        if (response.isNotBlank()) {
                            addMessage("Medora AI", response)
                        } else {
                            addMessage(
                                "Medora AI",
                                "The AI service returned an empty response."
                            )
                        }
                    }
                } else {
                    val error = json.optString(
                        "error",
                        "The AI service returned HTTP $status."
                    )

                    runOnUiThread {
                        addMessage("Medora AI", error)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "MedoraAI",
                    "AI request failed",
                    e
                )

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Could not get an AI response.",
                        Toast.LENGTH_SHORT
                    ).show()

                    addMessage(
                        "Medora AI",
                        "Couldn't connect to the AI service. " +
                                "Check your internet connection and try again."
                    )
                }
            } finally {
                connection?.disconnect()

                runOnUiThread {
                    requestInProgress = false
                    setLoading(false)
                }
            }
        }
    }

    private fun addMessage(sender: String, message: String) {
        val messageView = TextView(this).apply {
            text = "$sender\n$message"
            textSize = 16f

            setTextColor(
                android.graphics.Color.rgb(35, 43, 55)
            )

            setPadding(16, 12, 16, 12)

            setBackgroundResource(
                if (sender == "You") {
                    R.drawable.bg_ai_user_message
                } else {
                    R.drawable.bg_ai_assistant_message
                }
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.setMargins(0, 0, 0, 12)
            layoutParams = params
        }

        chatContainer.addView(messageView)

        chatScrollView.post {
            scrollChatToBottom()
        }
    }

    private fun scrollChatToBottom() {
        val distance = chatContainer.bottom -
                chatScrollView.height +
                chatScrollView.paddingBottom

        chatScrollView.smoothScrollTo(
            0,
            maxOf(0, distance)
        )
    }

    private fun setLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) {
            View.VISIBLE
        } else {
            View.GONE
        }

        sendButton.isEnabled = !isLoading
        messageInput.isEnabled = !isLoading

        sendButton.text = if (isLoading) {
            "Sending..."
        } else {
            "Send"
        }
    }

    override fun onDestroy() {
        networkExecutor.shutdownNow()
        super.onDestroy()
    }
}
