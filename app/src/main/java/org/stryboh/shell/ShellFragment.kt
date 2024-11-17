package org.stryboh.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat

class ShellFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var scanButton: Button
    private lateinit var clearButton: Button
    private lateinit var ipText: EditText
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
        ipText = view.findViewById(R.id.text_ip)

        scanButton.setOnClickListener {
            val cmd = ipText.text.toString()
            val command =
                "su --mount-master -c export PATH=\$PATH:/data/data/org.stryboh.shell/files/nmap/bin; $cmd"
            startNmapScan(command)
        }

        clearButton.setOnClickListener {
            outputText.text = ""
        }

        return view
    }

    private fun startNmapScan(command: String) {
        coroutineScope.launch {

            try {
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


                val process = Runtime.getRuntime().exec(command)
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