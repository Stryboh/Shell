package org.stryboh.shell

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class NmapScriptFragment : Fragment() {
    private lateinit var outputText: TextView
    private lateinit var targetInput: EditText
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
                savedUriString?.let { uriString ->
                    Uri.parse(uriString)?.let { uri ->
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("NmapScriptFragment", "Error taking persistent permission: ${e.message}")
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
                    Log.d("NmapScriptFragment", "Directory URI: $uri")

                    // Continue with scanning if this was called from scan operation
                    val target = targetInput.text.toString()
                    if (target.isNotBlank()) {
                        startNmapScan(target)
                    }
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
        requireActivity().title = "Nmap Scripts"
        val view = inflater.inflate(R.layout.layout_nmap_script, container, false)

        outputText = view.findViewById(R.id.text_output)
        targetInput = view.findViewById(R.id.text_target)
        scanButton = view.findViewById(R.id.button_scan)
        stopButton = view.findViewById(R.id.button_stop)
        deleteButton = view.findViewById(R.id.button_delete)
        progressBar = view.findViewById(R.id.progress_bar)

        scanButton.setOnClickListener {
            val target = targetInput.text.toString()
            if (target.isNotBlank()) {
                if (directoryUri == null) {
                    // If we don't have a directory URI, ask the user to select one
                    showDirectoryPicker()
                } else {
                    startNmapScan(target)
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a target", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopNmapScan()
        }

        deleteButton.setOnClickListener {
            if (directoryUri != null) {
                showDeleteXmlFileDialog()
            } else {
                Toast.makeText(requireContext(), "Please select a directory first", Toast.LENGTH_SHORT).show()
                showDirectoryPicker()
            }
        }

        return view
    }

    private fun showDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        directoryLauncher.launch(intent)
    }

    private fun startNmapScan(target: String) {
        // First check if we have a directory URI
        if (directoryUri == null) {
            showDirectoryPicker()
            return
        }

        scanJob = coroutineScope.launch {
            try {

                val currentDate = SimpleDateFormat("dd.MM.yyyy-HH.mm.ss", Locale.getDefault()).format(Date());

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

                val command = "${nmapDir.absolutePath}/nmap -sV -A -oX ${tempOutputFile.absolutePath} $target"

                withContext(Dispatchers.Main) {
                    outputText.append("\n> Starting scan of $target\n")
                    outputText.append("> Saving output to ${scanFile.name}\n")
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
                        outputText.append("\n$line")
                        progress = (progress + 1) % 100
                        progressBar.progress = progress
                    }
                }

                while (errorReader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        outputText.append("\n$line")
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
                    outputText.append("\n> Scan completed. Output saved to ${scanFile.name}\n")
                    scanButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputText.append("\nError: ${e.message}\n")
                    scanButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
                Log.e("NmapScriptFragment", "Error during scan: ${e.message}", e)
            }
        }
    }

    private fun stopNmapScan() {
        scanJob?.cancel()
        if (::process.isInitialized) {
            process.destroy()
        }
        scanButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        outputText.append("\n> Scan stopped by user\n")
    }

    private fun showDeleteXmlFileDialog() {
        val documentFile = DocumentFile.fromTreeUri(requireContext(), directoryUri!!)
        val xmlFiles = documentFile?.listFiles()?.filter { it.name?.endsWith(".xml") == true }

        if (xmlFiles.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No XML files found", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = xmlFiles.map { it.name ?: "Unknown" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete XML File")
            .setItems(fileNames) { _, which ->
                deleteXmlFile(xmlFiles[which])
            }
            .show()
    }

    private fun deleteXmlFile(file: DocumentFile) {
        if (file.delete()) {
            Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNmapScan()
    }
}
