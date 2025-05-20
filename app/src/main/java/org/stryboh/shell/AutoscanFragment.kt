package org.stryboh.shell

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import java.net.NetworkInterface
import android.net.Uri
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.*

class AutoscanFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var scanButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var process: Process
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var scanJob: Job? = null
    private lateinit var prefs: SharedPreferences
    private var directoryUri: Uri? = null

    // Activity result launcher to handle directory selection
    private lateinit var directoryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SharedPreferences
        prefs = requireContext().getSharedPreferences("NmapScriptPrefs", 0)

        // Get saved directory URI if available
        val savedUriString = prefs.getString("SCAN_DIRECTORY_URI", null)
        if (savedUriString != null) {
            directoryUri = Uri.parse(savedUriString)

            // Take persistent URI permission
            try {
                savedUriString.let { uriString ->
                    Uri.parse(uriString)?.let { uri ->
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("NmapFragment", "Error taking persistent permission: ${e.message}")
                // If we can't get permission, clear the saved URI
                directoryUri = null
                prefs.edit().remove("SCAN_DIRECTORY_URI").apply()
            }
        }

        // Initialize activity result launchers
        directoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // Save the selected directory URI
                    directoryUri = uri
                    prefs.edit().putString("SCAN_DIRECTORY_URI", uri.toString()).apply()

                    // Take persistent permission to access this directory
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    Toast.makeText(requireContext(), "Selected directory for scan results", Toast.LENGTH_SHORT).show()
                    Log.d("NmapFragment", "Directory URI: $uri")

                }
            } else {
                Toast.makeText(requireContext(), "No directory selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.layout_autoscan, container, false)

        requireActivity().title = "Autoscan - ${getLocalIpAddress()}"
        outputText = view.findViewById(R.id.autoscan_text_output)
        scanButton = view.findViewById(R.id.autoscan_button_start)
        stopButton = view.findViewById(R.id.autoscan_button_stop)
        deleteButton = view.findViewById(R.id.autoscan_button_delete)
        progressBar = view.findViewById(R.id.autoscan_progress_bar)

        scanButton.setOnClickListener {
                if (directoryUri == null) {
                    // If we don't have a directory URI, ask the user to select one
                    showDirectoryPicker()
                } else {
                    startNmapScan()
            }
        }

        stopButton.setOnClickListener {
            stopNmapScan()
        }

        deleteButton.setOnClickListener {
            outputText.text = ""
        }

        return view
    }

    private fun showDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        directoryLauncher.launch(intent)
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

    private fun getSubnetMaskPrefix(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val address = interfaceAddress.address
                    if (!address.isLoopbackAddress &&
                        address.isSiteLocalAddress &&
                        address is Inet4Address) {

                        // Получаем префикс подсети (например, 24 для /24)
                        val prefix = interfaceAddress.networkPrefixLength
                        return "/$prefix"
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
            Log.d("NmapFragment", "Root check failed: ${e.message}")
            false
        }
    }

    private fun appendColoredText(text: String, isCommand: Boolean = false) {
        val spannableBuilder = SpannableStringBuilder(outputText.text)

        if (isCommand || text.startsWith(">")) {
            // For command lines (starting with >), use red color
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
            // Normal text, just append
            spannableBuilder.append(text)
        }

        outputText.text = spannableBuilder
    }

    private fun startNmapScan() {
        val ip = getLocalIpAddress()
        val mask = getSubnetMaskPrefix()
        val local_target = "$ip" + "$mask"
        if (directoryUri == null) {
            showDirectoryPicker()
            return
        }

        scanJob = coroutineScope.launch {
            try {
                val currentDate = SimpleDateFormat("dd.MM.yyyy-HH.mm.ss", Locale.getDefault()).format(Date())
                val fileName = "scan_${currentDate}.xml"

                // Create a document file in the selected directory
                val documentFile = DocumentFile.fromTreeUri(requireContext(), directoryUri!!)
                val scanFile = documentFile?.createFile("text/xml", fileName)

                if (scanFile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to create output file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val nmapDir = File(requireContext().filesDir, "nmap/bin")
                // Create a temporary file to store the nmap output
                val tempOutputFile = File(requireContext().cacheDir, fileName)

                // Check for root access and construct command accordingly
                val isRooted = hasRootAccess()
                val command = if (isRooted) {
                    "su -c ${nmapDir.absolutePath}/nmap --system-dns $local_target -oX ${tempOutputFile.absolutePath}"
                } else {
                    "${nmapDir.absolutePath}/nmap --system-dns $local_target -oX ${tempOutputFile.absolutePath}"
                }

                Log.d("NmapFragment", "Running command: $command with root: $isRooted")

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Starting scan of $local_target\n", true)
                    appendColoredText("> Saving output to ${scanFile.name}\n", true)
                    if (isRooted) {
                        appendColoredText("> Running with root privileges\n", true)
                    } else {
                        appendColoredText("> Running without root privileges\n", true)
                    }
                    scanButton.visibility = View.GONE
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

                // Now copy the temporary file to the document file
                if (tempOutputFile.exists()) {
                    requireContext().contentResolver.openOutputStream(scanFile.uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(tempOutputFile.readText())
                        }
                    }
                    // Delete the temporary file
                    tempOutputFile.delete()
                }

                withContext(Dispatchers.Main) {
                    appendColoredText("\n> Scan completed. Output saved to ${scanFile.name}\n", true)
                    scanButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendColoredText("\nError: ${e.message}\n")
                    scanButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
                Log.e("NmapFragment", "Error during scan: ${e.message}", e)
            }
        }
    }

    private fun stopNmapScan() {
        scanJob?.cancel()
        if (::process.isInitialized) {
            try {
                // Check if running with root, we need to kill process differently
                if (hasRootAccess()) {
                    // Try to get PID of running nmap process and kill it with root
                    Runtime.getRuntime().exec("su -c pkill -f nmap")
                }
                process.destroy()
            } catch (e: Exception) {
                Log.e("NmapFragment", "Error stopping scan: ${e.message}")
            }
        }
        scanButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        appendColoredText("\n> Scan stopped by user\n", true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNmapScan()
    }
}
