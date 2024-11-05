package org.stryboh.shell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

class GUIFragment : Fragment() {

    private lateinit var hostView: HostView
    private lateinit var viewModeButton: ImageButton
    private lateinit var addModeButton: ImageButton
    private lateinit var removeModeButton: ImageButton
    private lateinit var removeLinkModeButton: ImageButton
    private lateinit var linkModeButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var loadButton: ImageButton
    private var movingHost: HostView.Host? = null
    private var selectedHosts = mutableListOf<HostView.Host>()
    private val handler = Handler()
    private var longPressRunnable: Runnable? = null
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var isDragging = false
    private var dialogShown = false
    private val longPressDuration = 500L
    private val moveThreshold = 30f
    private val gson = Gson()

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
                        movingHost = hostView.hosts.find { it.contains(x, y) }

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
                        val hostToRemove = hostView.hosts.find { it.contains(x, y) }

                        if (hostToRemove != null) {
                            hostView.removeHost(hostToRemove)
                        } else {
                            val linkToRemove = hostView.lines.find { line ->
                                line.first.contains(x, y) || line.second.contains(x, y)
                            }

                            if (linkToRemove != null) {
                                hostView.removeLink(linkToRemove.first, linkToRemove.second)
                            }
                        }
                    }

                    HostView.Mode.ADD -> {
                        val newHost = HostView.Host(x, y)
                        hostView.addHost(newHost)
                    }

                    HostView.Mode.LINK -> {
                        val selectedHost = hostView.hosts.find { it.contains(x, y) }

                        if (selectedHost != null) {
                            selectedHosts.add(selectedHost)

                            if (selectedHosts.size == 2) {
                                hostView.linkHosts(selectedHosts[0], selectedHosts[1])
                                selectedHosts.clear()
                            }
                        }
                    }

                    HostView.Mode.REMOVE_LINK -> {
                        val selectedHost = hostView.hosts.find { it.contains(x, y) }

                        if (selectedHost != null) {
                            selectedHosts.add(selectedHost)

                            if (selectedHosts.size == 2) {
                                hostView.removeLink(selectedHosts[0], selectedHosts[1])
                                selectedHosts.clear()
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
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
                    for (i in hostView.hosts ){
                        if (i.x + delta_x < hostView.width &&  i.y + delta_y < hostView.height &&
                            i.x + delta_x >= 0 &&  i.y + delta_y >= 0) {
                            canMove = true
                        }
                    }

                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                    isDragging = true
                    if (canMove) {
                        for (i in hostView.hosts ){
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
        editHostName.setText(host.hostName)
        editHostIP.setText(host.hostIP)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Host")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                host.hostName = editHostName.text.toString()
                host.hostIP = editHostIP.text.toString()
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
        val filesDir = requireContext().filesDir
        val files =
            filesDir.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()

        if (files.isNotEmpty()) {
            val fileNames = files.map { it.name }
            AlertDialog.Builder(requireContext())
                .setTitle("Select a Topology File")
                .setItems(fileNames.toTypedArray()) { _, which ->
                    loadTopology(files[which].toString())
                }
                .show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("No Files Found")
                .setMessage("No topology files are available to load.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveTopology(fileName: String) {
        val fileOutputStream: FileOutputStream

        try {
            if(!fileName.endsWith(".json"))
            {
                fileOutputStream = requireContext().openFileOutput("$fileName.json", Context.MODE_PRIVATE)
            }
            else{
                fileOutputStream = requireContext().openFileOutput(fileName, Context.MODE_PRIVATE)
            }

            val topology = mutableMapOf<String, Any>()
            topology["hosts"] = hostView.hosts

            val links = hostView.lines.map { link ->
                mapOf("hostId1" to link.first.id, "hostId2" to link.second.id)
            }

            topology["links"] = links
            val json = gson.toJson(topology)
            fileOutputStream.write(json.toByteArray())
            fileOutputStream.close()
            Toast.makeText(requireContext(), "Topology saved successfully.", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                "Failed to save topology: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadTopology(fileName: String) {
        val file = File(fileName)

        if (!file.exists()) {
            Toast.makeText(requireContext(), "File does not exist.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val json = file.readText()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val topology: Map<String, Any> = gson.fromJson(json, type)
            val hostsJson = topology["hosts"] as List<Map<String, Any>>
            val linksJson = topology["links"] as List<Map<String, Double>>
            hostView.hosts.clear()
            hostView.lines.clear()

            for (hostData in hostsJson) {
                val host = HostView.Host(
                    x = (hostData["x"] as Number).toFloat(),
                    y = (hostData["y"] as Number).toFloat(),
                    hostName = hostData["hostName"] as String,
                    hostIP = hostData["hostIP"] as String,
                    id = (hostData["id"] as Number).toInt()
                )
                hostView.addHost(host)
            }

            for (linkData in linksJson) {
                val hostId1 = (linkData["hostId1"] as Number).toInt()
                val hostId2 = (linkData["hostId2"] as Number).toInt()
                val host1 = hostView.hosts.find { it.id == hostId1 }
                val host2 = hostView.hosts.find { it.id == hostId2 }

                if (host1 != null && host2 != null) {
                    hostView.linkHosts(host1, host2)
                }
            }

            Toast.makeText(requireContext(), "Topology loaded successfully.", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(requireContext())
                .setTitle("Error Loading Topology")
                .setMessage("Failed to load the topology: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

}