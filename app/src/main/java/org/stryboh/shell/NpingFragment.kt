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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
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
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface

class NpingFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var targetInput: EditText
    private lateinit var pingButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var process: Process
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var pingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_nping, container, false)

        requireActivity().title = "Nping - ${getLocalIpAddress()}"
        outputText = view.findViewById(R.id.text_output)
        targetInput = view.findViewById(R.id.text_target)
        pingButton = view.findViewById(R.id.button_ping)
        stopButton = view.findViewById(R.id.button_stop)
        deleteButton = view.findViewById(R.id.button_delete)
        progressBar = view.findViewById(R.id.progress_bar)

        pingButton.setOnClickListener {
            val target = targetInput.text.toString()
            if (target.isNotBlank()) {
                try {
                    startNping(target)
                }
                catch (e: Exception) {
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a target", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopNping()
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
            Log.d("NpingFragment", "Root check failed: ${e.message}")
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

    private fun startNping(target: String) {
        pingJob = coroutineScope.launch {
            try {
                val npingDir = File(requireContext().filesDir, "nmap/bin")
                val isRooted = hasRootAccess()
                val command = if (isRooted) {
                    "su -c ${npingDir.absolutePath}/nping $target"
                } else {
                    "${npingDir.absolutePath}/nping $target"
                }

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Starting nping to $target\n", true)
                    if (isRooted) {
                        appendColoredText("> Running with root privileges\n", true)
                    } else {
                        appendColoredText("> Running without root privileges\n", true)
                    }
                    pingButton.visibility = View.GONE
                    stopButton.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                }

                process = Runtime.getRuntime().exec(command)

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                var progress = 0

                while (reader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        appendColoredText("\n$line")
                        progress = (progress + 1) % 100
                        progressBar.progress = progress
                    }
                }

                while (errorReader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        appendColoredText("\n$line")
                    }
                }

                process.waitFor()

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Nping completed\n", true)
                    pingButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendColoredText("\nError: ${e.message}\n")
                    pingButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
                Log.e("NpingFragment", "Error during nping: ${e.message}", e)
            }
        }
    }

    private fun stopNping() {
        pingJob?.cancel()
        if (::process.isInitialized) {
            try {
                if (hasRootAccess()) {
                    Runtime.getRuntime().exec("su -c pkill -f nping")
                }
                process.destroy()
            } catch (e: Exception) {
                Log.e("NpingFragment", "Error stopping nping: ${e.message}")
            }
        }
        pingButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        appendColoredText("\n> Nping stopped by user\n", true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNping()
    }
} 