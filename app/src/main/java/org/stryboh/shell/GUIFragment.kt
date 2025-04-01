package org.stryboh.shell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.os.Handler
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.sqrt
import java.io.File
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GUIFragment : Fragment() {

    private lateinit var hostView: HostView
    private lateinit var viewModeButton: ImageButton
    private lateinit var addModeButton: ImageButton
    private lateinit var removeModeButton: ImageButton
    private lateinit var removeLinkModeButton: ImageButton
    private lateinit var linkModeButton: ImageButton
    private lateinit var loadXMLButton: Button
    private lateinit var saveButton: ImageButton
    private lateinit var loadButton: ImageButton
    private var movingHost: HostView.Host? = null
    private val handler = Handler()
    private var selectedForLinkRemovalHosts = mutableListOf<HostView.Host>()
    private var selectedForLinkingHosts = mutableListOf<HostView.Host>()
    private var longPressRunnable: Runnable? = null
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var isDragging = false
    private var dialogShown = false
    private val longPressDuration = 500L
    private val moveThreshold = 30f
    private lateinit var prefs: SharedPreferences
    private var scanDirectoryUri: Uri? = null
    private lateinit var directoryLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        requireActivity().title = "GUI/Add hosts mode"
        val view = inflater.inflate(R.layout.layout_gui, container, false)
        hostView = view.findViewById(R.id.host_view)

        saveButton = view.findViewById(R.id.save_button)
        loadButton = view.findViewById(R.id.load_button)
        loadXMLButton = view.findViewById(R.id.xml_load_button)
        viewModeButton = view.findViewById(R.id.view_mode_button)
        addModeButton = view.findViewById(R.id.add_mode_button)
        removeModeButton = view.findViewById(R.id.remove_mode_button)
        removeLinkModeButton = view.findViewById(R.id.remove_link_mode_button)
        linkModeButton = view.findViewById(R.id.link_mode_button)


        saveButton.setOnClickListener {
            showSaveDialog()
        }
        loadButton.setOnClickListener {
            showFileSelectionDialog()
        }
        loadXMLButton.setOnClickListener {
            showNmapImportDialog()
        }
        viewModeButton.setOnClickListener {
            requireActivity().title = "GUI/View mode"
            hostView.currentMode = HostView.Mode.VIEW
        }
        addModeButton.setOnClickListener {
            requireActivity().title = "GUI/Add hosts mode"
            hostView.currentMode = HostView.Mode.ADD
        }
        removeModeButton.setOnClickListener {
            hostView.currentMode = HostView.Mode.REMOVE
            requireActivity().title = "GUI/Remove hosts mode"
        }
        removeLinkModeButton.setOnClickListener {
            requireActivity().title = "GUI/Remove link mode"
            hostView.currentMode = HostView.Mode.REMOVE_LINK
        }
        linkModeButton.setOnClickListener {
            requireActivity().title = "GUI/Link hosts mode"
            hostView.currentMode = HostView.Mode.LINK
        }

        hostView.setOnTouchListener { _, event ->
            handleTouchEvent(event)
            true
        }

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SharedPreferences
        prefs = requireContext().getSharedPreferences("NmapScriptPrefs", 0)

        // Get saved directory URI if available
        val savedUriString = prefs.getString("SCAN_DIRECTORY_URI", null)
        if (savedUriString != null) {
            scanDirectoryUri = Uri.parse(savedUriString)

            // Take persistent URI permission
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    scanDirectoryUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("GUIFragment", "Error taking persistent permission: ${e.message}")
                // If we can't get permission, clear the saved URI
                scanDirectoryUri = null
                prefs.edit().remove("SCAN_DIRECTORY_URI").apply()
            }
        }

        // Initialize activity result launcher for directory selection
        directoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // Save the selected directory URI
                    scanDirectoryUri = uri
                    prefs.edit().putString("SCAN_DIRECTORY_URI", uri.toString()).apply()

                    // Take persistent permission to access this directory
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    Toast.makeText(requireContext(), "Selected directory for scan results", Toast.LENGTH_SHORT).show()
                    Log.d("GUIFragment", "Directory URI: $uri")

                    // Now show the import dialog with the selected directory
                    showNmapImportDialog()
                }
            } else {
                Toast.makeText(requireContext(), "No directory selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleTouchEvent(event: MotionEvent) {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                initialX = x
                initialY = y

                when (hostView.currentMode) {
                    HostView.Mode.VIEW -> {
                        selectedForLinkRemovalHosts.clear()
                        selectedForLinkingHosts.clear()
                        movingHost = HostView.hosts.find { it.contains(x, y) }

                        if (movingHost != null) {
                            longPressRunnable = Runnable {
                                if (!dialogShown) {
                                    showEditDialog(movingHost!!)
                                    dialogShown = true
                                }
                            }
                            handler.postDelayed(longPressRunnable!!, longPressDuration)
                        }
                    }

                    HostView.Mode.REMOVE -> {
                        selectedForLinkRemovalHosts.clear()
                        selectedForLinkingHosts.clear()
                        val hostToRemove = HostView.hosts.find { it.contains(x, y) }

                        if (hostToRemove != null) {
                            hostView.removeHost(hostToRemove)
                        }
                    }

                    HostView.Mode.ADD -> {
                        selectedForLinkRemovalHosts.clear()
                        selectedForLinkingHosts.clear()
                        val newHost = HostView.Host(x, y)
                        hostView.addHost(newHost)
                    }

                    HostView.Mode.LINK -> {
                        selectedForLinkRemovalHosts.clear()
                        val selectedHost = HostView.hosts.find { it.contains(x, y) }

                        if (selectedHost != null) {
                            selectedForLinkingHosts.add(selectedHost)

                            if (selectedForLinkingHosts.size == 2) {
                                hostView.linkHosts(selectedForLinkingHosts[0], selectedForLinkingHosts[1])
                                selectedForLinkingHosts.clear()
                            }
                        }
                    }

                    HostView.Mode.REMOVE_LINK -> {
                        selectedForLinkingHosts.clear()
                        val selectedHost = HostView.hosts.find { it.contains(x, y) }

                        if (selectedHost != null) {

                            selectedForLinkRemovalHosts.add(selectedHost)
                            if (selectedForLinkRemovalHosts.size == 2) {
                                hostView.removeLink(selectedForLinkRemovalHosts[0], selectedForLinkRemovalHosts[1])
                                selectedForLinkRemovalHosts.clear()
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                selectedForLinkRemovalHosts.clear()
                selectedForLinkingHosts.clear()
                if (movingHost != null) {
                    val distance =
                        sqrt((x - initialX) * (x - initialX) + (y - initialY) * (y - initialY))
                    if (distance >= moveThreshold) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                        isDragging = true
                        hostView.moveHost(movingHost!!, x, y)
                    }
                }
                if (movingHost == null && hostView.currentMode == HostView.Mode.VIEW) {
                    val delta_x = x - initialX
                    val delta_y = y - initialY

                    var canMove = false
                    for (i in HostView.hosts ){
                        if (i.x + delta_x < hostView.width &&  i.y + delta_y < hostView.height &&
                            i.x + delta_x >= 0 &&  i.y + delta_y >= 0) {
                            canMove = true
                        }
                    }

                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                    isDragging = true
                    if (canMove) {
                        for (i in HostView.hosts ){
                            hostView.moveHost(i, i.x + delta_x, i.y + delta_y)
                        }
                    }
                    initialX = event.x
                    initialY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                if (isDragging) {
                    isDragging = false
                } else {
                    movingHost?.let {
                        if (!dialogShown) {
                            showEditDialog(it)
                            dialogShown = true
                        }
                    }
                }
                movingHost = null
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                movingHost = null
                isDragging = false
                dialogShown = false
            }
        }
    }

    private fun showEditDialog(host: HostView.Host) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_host, null)
        val editHostName: EditText = dialogView.findViewById(R.id.edit_host_name)
        val editHostIP: EditText = dialogView.findViewById(R.id.edit_host_ip)
        val editHostStatus: EditText = dialogView.findViewById(R.id.edit_host_status)
        val editHostOS: EditText = dialogView.findViewById(R.id.edit_host_os)
        val editHostPorts: EditText = dialogView.findViewById(R.id.edit_host_ports)
        val editHostUptime: EditText = dialogView.findViewById(R.id.edit_host_uptime)
        val editHostLastBoot: EditText = dialogView.findViewById(R.id.edit_host_lastboot)
        val editHostInfo: EditText = dialogView.findViewById(R.id.edit_host_info)

        editHostName.setText(host.hostName)
        editHostIP.setText(host.hostIP)
        editHostStatus.setText(host.status)
        editHostOS.setText(host.osInfo)
        editHostPorts.setText(host.ports)
        editHostUptime.setText(host.uptime)
        editHostLastBoot.setText(host.lastBoot)
        editHostInfo.setText(host.hostInfo)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Host")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                host.hostName = editHostName.text.toString()
                host.hostIP = editHostIP.text.toString()
                host.status = editHostStatus.text.toString()
                host.osInfo = editHostOS.text.toString()
                host.ports = editHostPorts.text.toString()
                host.uptime = editHostUptime.text.toString()
                host.lastBoot = editHostLastBoot.text.toString()
                host.hostInfo = editHostInfo.text.toString()
                hostView.invalidate()
                dialogShown = false
            }
            .setNegativeButton("Cancel") { _, _ ->
                dialogShown = false
            }
            .show()
    }

    private fun showSaveDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val input = EditText(requireContext())
        input.hint = "Enter filename"
        builder.setTitle("Save Topology")
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, _ ->
            val fileName = input.text.toString()
            if (fileName.isNotBlank()) {
                saveTopology(fileName)
            } else {
                Toast.makeText(requireContext(), "Filename cannot be empty", Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showFileSelectionDialog() {
        val dbHelper = DatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        val topologyNames = mutableSetOf<String>()
        try {
            val cursor = db.query(
                true,
                DatabaseHelper.TABLE_HOSTS,
                arrayOf(DatabaseHelper.COLUMN_TOPOLOGY_NAME),
                null,
                null,
                null,
                null,
                null,
                null
            )

            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TOPOLOGY_NAME))
                    topologyNames.add(name)
                }
            }

            val linksCursor = db.query(
                true,
                DatabaseHelper.TABLE_LINKS,
                arrayOf(DatabaseHelper.COLUMN_TOPOLOGY_NAME),
                null,
                null,
                null,
                null,
                null,
                null
            )

            linksCursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TOPOLOGY_NAME))
                    topologyNames.add(name)
                }
            }

            if (topologyNames.isNotEmpty()) {
                val items = arrayOf("Load", "Delete")
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Action")
                    .setItems(items) { _, actionWhich ->
                        when (actionWhich) {
                            0 -> { // Load
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Select a Topology to Load")
                                    .setItems(topologyNames.toTypedArray()) { _, which ->
                                        val selectedName = topologyNames.elementAt(which)
                                        loadTopology(selectedName)
                                    }
                                    .show()
                            }
                            1 -> { // Delete
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Select a Topology to Delete")
                                    .setItems(topologyNames.toTypedArray()) { _, which ->
                                        val selectedName = topologyNames.elementAt(which)
                                        deleteTopology(selectedName)
                                    }
                                    .show()
                            }
                        }
                    }
                    .show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Topologies Found")
                    .setMessage("No saved topologies are available.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to load topologies list: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        } finally {
            dbHelper.close()
        }
    }

    private fun deleteTopology(topologyName: String) {
        val dbHelper = DatabaseHelper(requireContext())
        val db = dbHelper.writableDatabase
        try {
            db.beginTransaction()

            // Delete from hosts table
            db.delete(
                DatabaseHelper.TABLE_HOSTS,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(topologyName)
            )

            // Delete from links table
            db.delete(
                DatabaseHelper.TABLE_LINKS,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(topologyName)
            )

            db.setTransactionSuccessful()
            Toast.makeText(requireContext(), "Topology deleted successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to delete topology: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            db.endTransaction()
            dbHelper.close()
        }
    }

    private fun showNmapImportDialog() {
        // First check if we have a directory URI, if not ask user to select one
        if (scanDirectoryUri == null) {
            showDirectoryPicker()
            return
        }

        try {
            // Get the DocumentFile that represents the directory
            val documentFile = DocumentFile.fromTreeUri(requireContext(), scanDirectoryUri!!)

            // List XML files in the directory
            val xmlFiles = documentFile?.listFiles()?.filter {
                it.name?.endsWith(".xml") == true
            }?.toMutableList()

            if (xmlFiles.isNullOrEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No XML files found in the selected directory",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Create dialog with custom layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_nmap_files, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.files_recycler_view)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())

            // Create and set adapter
            val adapter = NmapFileAdapter(
                xmlFiles,
                onFileClick = { selectedFile ->
                    try {
                        // Read the file content using ContentResolver
                        val inputStream = requireContext().contentResolver.openInputStream(selectedFile.uri)
                        val xmlContent = inputStream?.bufferedReader()?.use { it.readText() }

                        if (xmlContent != null) {
                            importNmapXml(xmlContent)
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Error reading file: Content is null",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Error reading file: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("GUIFragment", "File reading error", e)
                    }
                },
                onDeleteClick = { file ->
                    try {
                        if (file.delete()) {
                            Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error deleting file: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("GUIFragment", "File deletion error", e)
                    }
                }
            )
            recyclerView.adapter = adapter

            // Show dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Select XML File to Import")
                .setView(dialogView)
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error accessing storage: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("GUIFragment", "Storage access error", e)
        }
    }

    private fun showDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        directoryLauncher.launch(intent)
    }

    private fun importNmapXml(xmlContent: String) {
        try {
            val hosts = parseNmapXml(xmlContent)

            if (hosts.isEmpty()) {
                Toast.makeText(requireContext(), "No hosts found in the XML", Toast.LENGTH_SHORT).show()
                return
            }

            // Clear existing hosts before adding new ones
            val clearExisting = AlertDialog.Builder(requireContext())
                .setTitle("Import Hosts")
                .setMessage("Found ${hosts.size} hosts. Do you want to clear existing hosts?")
                .setPositiveButton("Yes") { _, _ ->
                    HostView.hosts.clear()
                    hostView.lines.clear()
                    addImportedHosts(hosts)
                }
                .setNegativeButton("No") { _, _ ->
                    addImportedHosts(hosts)
                }
                .create()

            clearExisting.show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                "Failed to import XML: ${e.message}",
                Toast.LENGTH_SHORT).show()
            Log.e("GUIFragment", "XML import error", e)
        }
    }

    private fun parseNmapXml(xmlInput: String): List<HostView.Host> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xmlInput.reader())

        val hosts = mutableListOf<HostView.Host>()
        var currentHost: HostView.Host? = null
        var inHost = false
        var inPorts = false
        var inPortsExtraports = false
        var inOS = false
        var currentOSInfo = StringBuilder()
        var currentPortsInfo = StringBuilder()

        val screenWidth = hostView.width.toFloat()
        val screenHeight = hostView.height.toFloat()
        val xOffset = 50f
        val yOffset = 50f

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "host" -> {
                            inHost = true
                            val randomX = xOffset + Math.random() * (screenWidth - 2 * xOffset)
                            val randomY = yOffset + Math.random() * (screenHeight - 2 * yOffset)
                            currentHost = HostView.Host(
                                x = randomX.toFloat(),
                                y = randomY.toFloat()
                            )
                        }
                        "status" -> {
                            if (inHost) {
                                currentHost?.status = parser.getAttributeValue(null, "state") ?: ""
                            }
                        }
                        "address" -> {
                            if (inHost && parser.getAttributeValue(null, "addrtype") == "ipv4") {
                                currentHost?.hostIP = parser.getAttributeValue(null, "addr") ?: ""
                            }
                        }
                        "hostname" -> {
                            if (inHost && currentHost?.hostName.isNullOrEmpty()) {
                                currentHost?.hostName = parser.getAttributeValue(null, "name") ?: ""
                            }
                        }
                        "ports" -> {
                            if (inHost) {
                                inPorts = true
                            }
                        }
                        "extraports" -> {
                            if (inPorts) {
                                inPortsExtraports = true
                                currentPortsInfo.append("Extraports: ")
                                    .append(parser.getAttributeValue(null, "state") ?: "")
                                    .append(" (")
                                    .append(parser.getAttributeValue(null, "count") ?: "")
                                    .append(")\n")
                            }
                        }
                        "port" -> {
                            if (inPorts && !inPortsExtraports) {
                                val protocol = parser.getAttributeValue(null, "protocol") ?: ""
                                val portId = parser.getAttributeValue(null, "portid") ?: ""
                                currentPortsInfo.append("$protocol/$portId ")
                            }
                        }
                        "state" -> {
                            if (inPorts && !inPortsExtraports) {
                                currentPortsInfo.append("(")
                                    .append(parser.getAttributeValue(null, "state") ?: "")
                                    .append(") ")
                            }
                        }
                        "service" -> {
                            if (inPorts && !inPortsExtraports) {
                                val service = parser.getAttributeValue(null, "name") ?: ""
                                val product = parser.getAttributeValue(null, "product") ?: ""
                                val version = parser.getAttributeValue(null, "version") ?: ""

                                currentPortsInfo.append("$service")
                                if (product.isNotEmpty()) {
                                    currentPortsInfo.append(" $product")
                                }
                                if (version.isNotEmpty()) {
                                    currentPortsInfo.append(" $version")
                                }
                                currentPortsInfo.append("\n")
                            }
                        }
                        "os" -> {
                            if (inHost) {
                                inOS = true
                            }
                        }
                        "osmatch" -> {
                            if (inOS) {
                                val name = parser.getAttributeValue(null, "name") ?: ""
                                val accuracy = parser.getAttributeValue(null, "accuracy") ?: ""
                                if (name.isNotEmpty()) {
                                    currentOSInfo.append("OS: $name (Accuracy: $accuracy%)\n")
                                }
                            }
                        }
                        "uptime" -> {
                            if (inHost) {
                                val seconds = parser.getAttributeValue(null, "seconds") ?: ""
                                val lastboot = parser.getAttributeValue(null, "lastboot") ?: ""
                                if (seconds.isNotEmpty()) {
                                    currentHost?.uptime = seconds
                                }
                                if (lastboot.isNotEmpty()) {
                                    currentHost?.lastBoot = lastboot
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "host" -> {
                            inHost = false
                            currentHost?.ports = currentPortsInfo.toString()
                            currentHost?.osInfo = currentOSInfo.toString()
                            currentHost?.hostInfo = "Status: ${currentHost?.status}\n" +
                                    "${currentHost?.osInfo}" +
                                    "Uptime: ${currentHost?.uptime}s, Last boot: ${currentHost?.lastBoot}"

                            currentHost?.let { hosts.add(it) }
                            currentHost = null
                            currentPortsInfo = StringBuilder()
                            currentOSInfo = StringBuilder()
                        }
                        "ports" -> {
                            inPorts = false
                            inPortsExtraports = false
                        }
                        "extraports" -> {
                            inPortsExtraports = false
                        }
                        "os" -> {
                            inOS = false
                        }
                    }
                }
            }
            parser.next()
        }
        return hosts
    }

    private fun addImportedHosts(hosts: List<HostView.Host>) {
        for (host in hosts) {
            hostView.addHost(host)
        }

        // Automatically link hosts that are from the same subnet
        if (hosts.size > 1) {
            val subnets = mutableMapOf<String, MutableList<HostView.Host>>()

            // Group hosts by subnet (first 3 octets of IP)
            for (host in hosts) {
                val ip = host.hostIP
                val subnet = ip.split(".").take(3).joinToString(".")
                if (!subnets.containsKey(subnet)) {
                    subnets[subnet] = mutableListOf()
                }
                subnets[subnet]?.add(host)
            }

            // Link hosts within the same subnet
            for (subnet in subnets.values) {
                if (subnet.size > 1) {
                    for (i in 0 until subnet.size - 1) {
                        hostView.linkHosts(subnet[i], subnet[i + 1])
                    }
                }
            }
        }

        Toast.makeText(requireContext(),
            "Successfully imported ${hosts.size} hosts",
            Toast.LENGTH_SHORT).show()

        // Switch to view mode to see the imported hosts
        hostView.currentMode = HostView.Mode.VIEW
        requireActivity().title = "GUI/View mode"
    }

    private fun saveTopology(fileName: String) {
        val dbHelper = DatabaseHelper(requireContext())
        val db = dbHelper.writableDatabase
        try {
            db.beginTransaction()
            db.delete(
                DatabaseHelper.TABLE_HOSTS,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(fileName)
            )
            db.delete(
                DatabaseHelper.TABLE_LINKS,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(fileName)
            )

            for (host in HostView.hosts) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_TOPOLOGY_NAME, fileName)
                    put(DatabaseHelper.COLUMN_HOST_ID, host.id)
                    put(DatabaseHelper.COLUMN_X, host.x)
                    put(DatabaseHelper.COLUMN_Y, host.y)
                    put(DatabaseHelper.COLUMN_HOST_NAME, host.hostName)
                    put(DatabaseHelper.COLUMN_HOST_IP, host.hostIP)
                    put(DatabaseHelper.COLUMN_HOST_INFO, host.hostInfo)
                    put(DatabaseHelper.COLUMN_HOST_STATUS, host.status)
                    put(DatabaseHelper.COLUMN_OS_INFO, host.osInfo)
                    put(DatabaseHelper.COLUMN_PORTS, host.ports)
                    put(DatabaseHelper.COLUMN_UPTIME, host.uptime)
                    put(DatabaseHelper.COLUMN_LASTBOOT, host.lastBoot)
                }
                db.insert(DatabaseHelper.TABLE_HOSTS, null, values)
            }
            for (link in hostView.lines) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_TOPOLOGY_NAME, fileName)
                    put(DatabaseHelper.COLUMN_HOST_ID1, link.first.id)
                    put(DatabaseHelper.COLUMN_HOST_ID2, link.second.id)
                }
                db.insert(DatabaseHelper.TABLE_LINKS, null, values)
            }
            db.setTransactionSuccessful()
            Toast.makeText(requireContext(), "Topology saved successfully.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                "Failed to save topology: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            db.endTransaction()
            dbHelper.close()
        }
    }

    private fun loadTopology(fileName: String) {
        val dbHelper = DatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        try {
            val hostsCursor = db.query(
                DatabaseHelper.TABLE_HOSTS,
                null,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(fileName),
                null,
                null,
                null
            )
            if (!hostsCursor.moveToFirst()) {
                Toast.makeText(requireContext(), "Topology does not exist.", Toast.LENGTH_SHORT).show()
                return
            }
            HostView.hosts.clear()
            hostView.lines.clear()
            do {
                val hostId = hostsCursor.getInt(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_ID))
                val host = HostView.Host(
                    x = hostsCursor.getFloat(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_X)),
                    y = hostsCursor.getFloat(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_Y)),
                    hostName = hostsCursor.getString(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_NAME)),
                    hostIP = hostsCursor.getString(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_IP)),
                    hostInfo = hostsCursor.getString(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_INFO)),
                    id = hostId
                )

                // Load additional Nmap data if available
                try {
                    val portsIdx = hostsCursor.getColumnIndex(DatabaseHelper.COLUMN_PORTS)
                    if (portsIdx != -1) {
                        host.ports = hostsCursor.getString(portsIdx) ?: ""
                    }

                    val osInfoIdx = hostsCursor.getColumnIndex(DatabaseHelper.COLUMN_OS_INFO)
                    if (osInfoIdx != -1) {
                        host.osInfo = hostsCursor.getString(osInfoIdx) ?: ""
                    }

                    val statusIdx = hostsCursor.getColumnIndex(DatabaseHelper.COLUMN_HOST_STATUS)
                    if (statusIdx != -1) {
                        host.status = hostsCursor.getString(statusIdx) ?: ""
                    }

                    val uptimeIdx = hostsCursor.getColumnIndex(DatabaseHelper.COLUMN_UPTIME)
                    if (uptimeIdx != -1) {
                        host.uptime = hostsCursor.getString(uptimeIdx) ?: ""
                    }

                    val lastBootIdx = hostsCursor.getColumnIndex(DatabaseHelper.COLUMN_LASTBOOT)
                    if (lastBootIdx != -1) {
                        host.lastBoot = hostsCursor.getString(lastBootIdx) ?: ""
                    }
                } catch (e: Exception) {
                    Log.e("GUIFragment", "Error loading Nmap data: ${e.message}")
                }

                hostView.addHost(host)
                Log.d("STATUS", "ADDED HOST $host")
            } while (hostsCursor.moveToNext())
            hostsCursor.close()
            val linksCursor = db.query(
                DatabaseHelper.TABLE_LINKS,
                null,
                "${DatabaseHelper.COLUMN_TOPOLOGY_NAME} = ?",
                arrayOf(fileName),
                null,
                null,
                null
            )
            while (linksCursor.moveToNext()) {
                val hostId1 = linksCursor.getInt(linksCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_ID1))
                val hostId2 = linksCursor.getInt(linksCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_ID2))
                val host1 = HostView.hosts.find { it.id == hostId1 }
                val host2 = HostView.hosts.find { it.id == hostId2 }

                if (host1 != null && host2 != null) {
                    hostView.linkHosts(host1, host2)
                    Log.d("STATUS", "LINKED HOSTS: $host1 and $host2")
                }
            }
            linksCursor.close()
            Toast.makeText(requireContext(), "Topology loaded successfully.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(requireContext())
                .setTitle("Error Loading Topology")
                .setMessage("Failed to load topology: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        } finally {
            dbHelper.close()
        }
    }

    class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        companion object {
            private const val DATABASE_NAME = "network_topology.db"
            private const val DATABASE_VERSION = 1

            // Hosts table
            const val TABLE_HOSTS = "hosts"
            const val COLUMN_HOST_ID = "host_id"
            const val COLUMN_TOPOLOGY_NAME = "topology_name"
            const val COLUMN_X = "x"
            const val COLUMN_Y = "y"
            const val COLUMN_HOST_NAME = "host_name"
            const val COLUMN_HOST_IP = "host_ip"
            const val COLUMN_HOST_INFO = "host_info"
            const val COLUMN_HOST_STATUS = "host_status"
            const val COLUMN_OS_INFO = "os_info"
            const val COLUMN_PORTS = "ports"
            const val COLUMN_UPTIME = "uptime"
            const val COLUMN_LASTBOOT = "lastboot"

            // Links table
            const val TABLE_LINKS = "links"
            const val COLUMN_HOST_ID1 = "host_id1"
            const val COLUMN_HOST_ID2 = "host_id2"
        }

        override fun onCreate(db: SQLiteDatabase) {
            val createHostsTable = """
            CREATE TABLE $TABLE_HOSTS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TOPOLOGY_NAME TEXT NOT NULL,
                $COLUMN_HOST_ID INTEGER NOT NULL,
                $COLUMN_X REAL NOT NULL,
                $COLUMN_Y REAL NOT NULL,
                $COLUMN_HOST_NAME TEXT NOT NULL,
                $COLUMN_HOST_IP TEXT NOT NULL,
                $COLUMN_HOST_INFO TEXT NOT NULL,
                $COLUMN_HOST_STATUS TEXT,
                $COLUMN_OS_INFO TEXT,
                $COLUMN_PORTS TEXT,
                $COLUMN_UPTIME TEXT,
                $COLUMN_LASTBOOT TEXT
            )
        """.trimIndent()

            val createLinksTable = """
            CREATE TABLE $TABLE_LINKS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TOPOLOGY_NAME TEXT NOT NULL,
                $COLUMN_HOST_ID1 INTEGER NOT NULL,
                $COLUMN_HOST_ID2 INTEGER NOT NULL
            )
        """.trimIndent()

            db.execSQL(createHostsTable)
            db.execSQL(createLinksTable)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_HOSTS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_LINKS")
            onCreate(db)
        }
    }
}