package org.stryboh.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class HostView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    val lines = mutableListOf<Pair<Host, Host>>()
    private val paintLine =
        Paint().apply {
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
            for (host in Companion.hosts) {
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
        val paint =
            Paint().apply {
                color = Color.CYAN
                textSize = 40f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(host.hostName, host.x, host.y + 90, paint)
        canvas.drawText(host.hostIP, host.x, host.y + 130, paint)
    }

    fun addHost(host: Host) {
        Companion.hosts.add(host)
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

        lines.removeIf {
            (it.first == host1 && it.second == host2) || (it.first == host2 && it.second == host1)
        }
        invalidate()
    }

    fun removeHost(host: Host) {
        Companion.hosts.remove(host)
        lines.removeAll { it.first == host || it.second == host }
        invalidate()
    }

    data class Host(
        var x: Float,
        var y: Float,
        var hostName: String = "",
        var hostIP: String = "",
        var hostInfo: String = "",
        var ports: String = "",
        var id: Int = generateId(),
        var osInfo: String = "",
        var status: String = "",
        var uptime: String = "",
        var lastBoot: String = ""
    ) {
        companion object {
            private var nextId = 1
            const val RADIUS = 50f

            fun generateId(): Int {
                return nextId++
            }
        }

        fun constructor(
            x: Float,
            y: Float,
            hostName: String = "",
            hostIP: String = "",
            hostInfo: String = "",
            id: Int = generateId(),
            ports: String = "",
            osInfo: String = "",
            status: String = "",
            uptime: String = "",
            lastBoot: String = ""
        ) {
            this.x = x
            this.y = y
            this.hostIP = hostIP
            this.hostName = hostName
            this.hostInfo = hostInfo
            this.id = id
            this.ports = ports
            this.osInfo = osInfo
            this.status = status
            this.uptime = uptime
            this.lastBoot = lastBoot
            nextId = HostView.Companion.hosts.size + 1
        }

        fun contains(px: Float, py: Float): Boolean {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy <= RADIUS * RADIUS
        }
    }

    data class Port(
        val portId: Int,
        val protocol: String,
        val state: String,
        val service: String,
        val product: String = "",
        val version: String = "",
        val extraInfo: String = ""
    )

    companion object {
        val hosts = mutableListOf<Host>()

        fun parseNmapXml(xmlInput: String, context: Context): List<Host> {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xmlInput.reader())

            val hosts = mutableListOf<Host>()
            var currentHost: Host? = null
            var inHost = false
            var inPorts = false
            var inPortsExtraports = false
            var inOS = false
            var currentOSInfo = StringBuilder()
            var currentPortsInfo = StringBuilder()

            var screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
            var screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
            var xOffset = 50f
            var yOffset = 50f

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "host" -> {
                                inHost = true
                                val randomX = xOffset + Math.random() * (screenWidth - 2 * xOffset)
                                val randomY = yOffset + Math.random() * (screenHeight - 2 * yOffset)
                                currentHost = Host(
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
    }

    data class Link(var hostId1: Int, var hostId2: Int)
}
