package org.stryboh.shell

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.NetworkInterface
import java.io.File

class NcatFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var targetInput: EditText
    private lateinit var textInput: EditText
    private lateinit var inputContainer: LinearLayout
    private lateinit var connectButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var process: Process
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var connectionJob: Job? = null
    private var outputWriter: OutputStreamWriter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_ncat, container, false)

        requireActivity().title = "Ncat - ${getLocalIpAddress()}"
        outputText = view.findViewById(R.id.text_output)
        targetInput = view.findViewById(R.id.text_target)
        textInput = view.findViewById(R.id.text_input)
        inputContainer = view.findViewById(R.id.input_container)
        connectButton = view.findViewById(R.id.button_connect)
        stopButton = view.findViewById(R.id.button_stop_ncat)
        deleteButton = view.findViewById(R.id.button_delete)
        sendButton = view.findViewById(R.id.button_send)

        // Configure text input for sending messages
        textInput.imeOptions = EditorInfo.IME_ACTION_SEND
        textInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = v.text.toString()
                if (text.isNotBlank()) {
                    sendText(text)
                    v.text = ""
                }
                true
            } else {
                false
            }
        }

        sendButton.setOnClickListener {
            val text = textInput.text.toString()
            if (text.isNotBlank()) {
                sendText(text)
                textInput.text.clear()
            }
        }

        connectButton.setOnClickListener {
            val target = targetInput.text.toString()
            if (target.isNotBlank()) {
                try {
                    startNcatConnection(target)
                }
                catch (e: Exception) {
                }

            } else {
                Toast.makeText(requireContext(), "Please enter a target", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopNcatConnection()
        }

        deleteButton.setOnClickListener {
            outputText.text = ""
        }

        return view
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces: List<NetworkInterface> =
                NetworkInterface.getNetworkInterfaces().toList()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses.toList()
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c echo root_test")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val response = reader.readLine()
            val exitValue = process.waitFor()

            exitValue == 0 && response == "root_test"
        } catch (e: Exception) {
            Log.d("NcatFragment", "Root check failed: ${e.message}")
            false
        }
    }

    private fun appendColoredText(text: String, isCommand: Boolean = false) {
        val spannableBuilder = SpannableStringBuilder(outputText.text)

        if (isCommand || text.startsWith(">")) {
            val redColor = ContextCompat.getColor(requireContext(), R.color.red)
            val coloredText = SpannableString(text)
            coloredText.setSpan(
                ForegroundColorSpan(redColor),
                0,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableBuilder.append(coloredText)
        } else {
            spannableBuilder.append(text)
        }

        outputText.text = spannableBuilder
    }

    private fun startNcatConnection(target: String) {
        connectionJob = coroutineScope.launch {
            try {
                val ncatDir = File(requireContext().filesDir, "nmap/bin")
                val isRooted = hasRootAccess()
                val command = if (isRooted) {
                    "su -c ${ncatDir.absolutePath}/ncat $target"
                } else {
                    "${ncatDir.absolutePath}/ncat $target"
                }

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Starting connection to $target\n", true)
                    if (isRooted) {
                        appendColoredText("> Running with root privileges\n", true)
                    } else {
                        appendColoredText("> Running without root privileges\n", true)
                    }
                    connectButton.visibility = View.GONE
                    stopButton.visibility = View.VISIBLE
                    inputContainer.visibility = View.VISIBLE
                }

                process = Runtime.getRuntime().exec(command)
                outputWriter = OutputStreamWriter(process.outputStream)

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

               //Read output
                launch(Dispatchers.IO) {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        withContext(Dispatchers.Main) {
                            appendColoredText("\n$line")
                        }
                    }
                }

                // Read errors
                launch(Dispatchers.IO) {
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        withContext(Dispatchers.Main) {
                            appendColoredText("\n$line")
                        }
                    }
                }

                process.waitFor()

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Connection closed\n", true)
                    connectButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    inputContainer.visibility = View.GONE
                    Log.d("STATUS","REACHED END")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendColoredText("\nError: ${e.message}\n")
                    connectButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    inputContainer.visibility = View.GONE
                }
                Log.e("NcatFragment", "Error during connection: ${e.message}", e)
            }
        }
    }

    private fun sendText(text: String) {
        coroutineScope.launch {
            try {
                outputWriter?.write("$text\n")
                outputWriter?.flush()
                withContext(Dispatchers.Main) {
                    appendColoredText("\n> $text\n", true)
                }
            } catch (e: Exception) {
                Log.e("NcatFragment", "Error sending text: ${e.message}", e)
            }
        }
    }

    private fun stopNcatConnection() {
        connectionJob?.cancel()
        if (::process.isInitialized) {
            try {
                if (hasRootAccess()) {
                    Runtime.getRuntime().exec("su -c pkill -f ncat")
                }
                else
                    process.destroy()
            } catch (e: Exception) {
                Log.e("NcatFragment", "Error stopping connection: ${e.message}")
            }
        }
        connectButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        inputContainer.visibility = View.GONE
        appendColoredText("\n> Connection stopped by user\n", true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNcatConnection()
    }
} 