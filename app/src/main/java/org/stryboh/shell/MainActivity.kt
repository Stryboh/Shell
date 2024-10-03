package org.stryboh.shell

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader


class MainActivity : ComponentActivity() {

    private lateinit var outputText: TextView
    private lateinit var scanButton: Button
    private lateinit var clearButton: Button
    private lateinit var ipText: EditText
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main)

        scanButton = findViewById(R.id.button_run)
        clearButton = findViewById(R.id.button_clear)
        ipText = findViewById(R.id.text_ip)

        scanButton.setOnClickListener {
            val cmd = ipText.text.toString()
            val command = "su --mount-master -c export PATH=\$PATH:/data/opt; $cmd"
            startNmapScan(command)
        }
        clearButton.setOnClickListener {
            outputText.text = ""
        }
    }

    override fun onStart() {
        super.onStart()
        outputText = findViewById(R.id.text_output)
        outputText.setTextIsSelectable(true)
        outputText.isFocusable = true
        outputText.isFocusableInTouchMode = true
    }

    private fun startNmapScan(command: String) {
        coroutineScope.launch {
            try {
                val process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        outputText.append("\n" + line)
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
