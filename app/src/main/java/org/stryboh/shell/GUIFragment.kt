@file:Suppress("DEPRECATION")

package org.stryboh.shell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import kotlin.math.sqrt

class GUIFragment : Fragment() {

    private lateinit var hostView: HostView
    private lateinit var modeButton: Button
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_gui, container, false)
        hostView = view.findViewById(R.id.host_view)
        modeButton = view.findViewById(R.id.link_mode_button)
        modeButton.setOnClickListener {
            toggleMode()
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
                if (hostView.currentMode == HostView.Mode.VIEW) {
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
                } else {
                    
                    handleAddOrLinkHosts(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (movingHost != null) {
                    val distance = sqrt((x - initialX) * (x - initialX) + (y - initialY) * (y - initialY))
                    if (distance >= moveThreshold) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                        isDragging = true 
                        hostView.moveHost(movingHost!!, x, y)
                    }
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

    @SuppressLint("SetTextI18n")
    private fun toggleMode() {
        when (hostView.currentMode) {
            HostView.Mode.ADD -> {
                hostView.currentMode = HostView.Mode.LINK
                modeButton.text = "View Mode"
            }
            HostView.Mode.LINK -> {
                hostView.currentMode = HostView.Mode.VIEW
                modeButton.text = "Add Mode"
            }
            HostView.Mode.VIEW -> {
                hostView.currentMode = HostView.Mode.ADD
                modeButton.text = "Link Mode"
            }
        }
    }

    private fun handleAddOrLinkHosts(x: Float, y: Float) {
        when (hostView.currentMode) {
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
            HostView.Mode.VIEW -> {
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
}
