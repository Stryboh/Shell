package org.stryboh.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View

class HostView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    val hosts = mutableListOf<Host>()
    val lines = mutableListOf<Pair<Host, Host>>()
    private val paintLine = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private val computerIcon: Drawable? = context.getDrawable(R.drawable.ic_computer)

    enum class Mode {
        ADD,
        LINK,
        VIEW,
        REMOVE,
        REMOVE_LINK
    }

    var currentMode = Mode.ADD
        set(value) {
            field = value
            selectedHosts.clear()
        }

    private var selectedHosts = mutableListOf<Host>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((host1, host2) in lines) {
            canvas.drawLine(host1.x, host1.y, host2.x, host2.y, paintLine)
        }

        computerIcon?.let { drawable ->
            for (host in hosts) {
                drawable.setBounds(
                    (host.x - drawable.intrinsicWidth / 2).toInt(),
                    (host.y - drawable.intrinsicHeight / 2).toInt(),
                    (host.x + drawable.intrinsicWidth / 2).toInt(),
                    (host.y + drawable.intrinsicHeight / 2).toInt()
                )
                drawable.draw(canvas)
                drawHostInfo(canvas, host)
            }
        }
    }

    private fun drawHostInfo(canvas: Canvas, host: Host) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(host.hostName, host.x, host.y + 90, paint)
        canvas.drawText(host.hostIP, host.x, host.y + 130, paint)
    }

    fun addHost(host: Host) {
        hosts.add(host)
        invalidate()
    }

    fun linkHosts(host1: Host, host2: Host) {
        lines.add(Pair(host1, host2))
        invalidate()
    }

    fun moveHost(host: Host, x: Float, y: Float) {
        host.x = x
        host.y = y
        invalidate()
    }

    fun removeLink(host1: Host, host2: Host) {
        
        lines.removeIf { (it.first == host1 && it.second == host2) || (it.first == host2 && it.second == host1) }
        invalidate() 
    }

    fun removeHost(host: Host) {
        hosts.remove(host)
        lines.removeAll { it.first == host || it.second == host }
        invalidate()
    }

    data class Host(
        var x: Float,
        var y: Float,
        var hostName: String = "",
        var hostIP: String = "",
        var id: Int = generateId()
    ) {
        companion object {
            private var nextId = 1
            const val RADIUS = 50f

            fun generateId(): Int {
                return nextId++
            }
        }

        fun contains(px: Float, py: Float): Boolean {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy <= RADIUS * RADIUS
        }
    }

    data class Link(
        var hostId1: Int,
        var hostId2: Int
    )

}
