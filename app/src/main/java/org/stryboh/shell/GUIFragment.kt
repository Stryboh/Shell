package org.stryboh.shell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
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
                        selectedForLinkRemovalHosts.clear()
                        selectedForLinkingHosts.clear()
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
                        selectedForLinkRemovalHosts.clear()
                        selectedForLinkingHosts.clear()
                        val hostToRemove = hostView.hosts.find { it.contains(x, y) }

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
                        val selectedHost = hostView.hosts.find { it.contains(x, y) }

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
                        val selectedHost = hostView.hosts.find { it.contains(x, y) }

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
                AlertDialog.Builder(requireContext())
                    .setTitle("Select a Topology")
                    .setItems(topologyNames.toTypedArray()) { _, which ->
                        val selectedName = topologyNames.elementAt(which)
                        loadTopology(selectedName)
                    }
                    .show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Topologies Found")
                    .setMessage("No saved topologies are available to load.")
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

            for (host in hostView.hosts) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_TOPOLOGY_NAME, fileName)
                    put(DatabaseHelper.COLUMN_HOST_ID, host.id)
                    put(DatabaseHelper.COLUMN_X, host.x)
                    put(DatabaseHelper.COLUMN_Y, host.y)
                    put(DatabaseHelper.COLUMN_HOST_NAME, host.hostName)
                    put(DatabaseHelper.COLUMN_HOST_IP, host.hostIP)
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
            hostView.hosts.clear()
            hostView.lines.clear()
            do {
                val host = HostView.Host(
                    x = hostsCursor.getFloat(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_X)),
                    y = hostsCursor.getFloat(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_Y)),
                    hostName = hostsCursor.getString(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_NAME)),
                    hostIP = hostsCursor.getString(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_IP)),
                    id = hostsCursor.getInt(hostsCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_HOST_ID))
                )
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
                val host1 = hostView.hosts.find { it.id == hostId1 }
                val host2 = hostView.hosts.find { it.id == hostId2 }

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

            // Таблица хостов
            const val TABLE_HOSTS = "hosts"
            const val COLUMN_HOST_ID = "host_id"
            const val COLUMN_TOPOLOGY_NAME = "topology_name"
            const val COLUMN_X = "x"
            const val COLUMN_Y = "y"
            const val COLUMN_HOST_NAME = "host_name"
            const val COLUMN_HOST_IP = "host_ip"

            // Таблица связей
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
                $COLUMN_HOST_IP TEXT NOT NULL
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
