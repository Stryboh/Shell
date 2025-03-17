package org.stryboh.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class ShellFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var scanButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var ipText: EditText
    lateinit var process: Process
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        requireActivity().title = "Shell"
        val view = inflater.inflate(R.layout.layout_shell, container, false)
        outputText = view.findViewById(R.id.text_output)
        scanButton = view.findViewById(R.id.button_run)
        clearButton = view.findViewById(R.id.button_clear)
        stopButton = view.findViewById(R.id.button_stop)
        ipText = view.findViewById(R.id.text_ip)

        scanButton.setOnClickListener {
            var cmd = ipText.text.toString()
            startNmapScan(cmd)
        }

        stopButton.setOnClickListener {
            if (::process.isInitialized)
                process.destroy()
        }

        clearButton.setOnClickListener {
            outputText.text = ""
        }

        return view
    }

    private fun startNmapScan(command: String) {
        coroutineScope.launch {

            try {
                val nmapDir = File(requireContext().filesDir, "nmap/bin")
                val spannableBuilder = SpannableStringBuilder(outputText.text)
                val redColor = context?.let { ContextCompat.getColor(it, R.color.red) }
                val redText = SpannableString("\n>${ipText.text}")
                redText.setSpan(
                    redColor?.let { ForegroundColorSpan(it) },
                    0,
                    redText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableBuilder.append(redText)
                outputText.text = spannableBuilder

                var toExec: String

                if (command.contains("nmap"))
                    toExec = command.replace("nmap", "${nmapDir.absolutePath}/nmap --system-dns")

                else if(command.contains("ncat"))
                    toExec  = command.replace("ncat", "${nmapDir.absolutePath}/ncat")

                else if(command.contains("nping"))
                    toExec = command.replace("nping", "${nmapDir.absolutePath}/nping")

                else
                    toExec = command

                process = Runtime.getRuntime().exec(toExec)

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        outputText.append("\n$line")
                    }
                }

                while (errorReader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        outputText.append("\n$line")
                    }
                }
                process.waitFor()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                }
            }
        }
    }
}