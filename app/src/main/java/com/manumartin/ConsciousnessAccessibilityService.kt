package com.manumartin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ConsciousnessAccessibilityService : AccessibilityService() {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val censorViews = mutableListOf<CensorView>()
    private val blocklist = setOf(
        // Domains
        "pornhub.com", "xvideos.com", "xnxx.com", "superchatlive.com",
        // Keywords
        "nude", "porn", "sexy", "adult entertainment", "erotic","sex"
    )
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf("com.android.chrome", "org.mozilla.firefox", "com.duckduckgo.mobile.android")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootNode = rootInActiveWindow ?: return

            findUrlBar(rootNode)?.text?.let { url ->
                if (isBlocked(url.toString())) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handler.post {
                        Toast.makeText(
                            applicationContext,
                            "Incognito browsing of this content is blocked. Please switch to normal browsing for content filtering.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            rootNode.recycle()
        }
    }

    private fun isBlocked(url: String): Boolean {
        return blocklist.any { url.contains(it, ignoreCase = true) }
    }

    private fun findUrlBar(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Example for Chrome, Firefox, and DuckDuckGo
        val browserUrlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/url_bar_title",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )

        browserUrlBarIds.forEach { id ->
            val nodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }

        for (i in 0 until nodeInfo.childCount) {
            val child = nodeInfo.getChild(i)
            val found = findUrlBar(child)
            if (found != null) {
                return found
            }
            child?.recycle()
        }

        return null
    }


    override fun onInterrupt() {
        // Not needed for this implementation
    }

    companion object {
        var instance: ConsciousnessAccessibilityService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        clearCensorViews()
    }

    fun addCensorView(bounds: Rect) {
        val censorView = CensorView(this)
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            bounds.left,
            bounds.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }
        windowManager.addView(censorView, params)
        censorViews.add(censorView)
    }

    fun clearCensorViews() {
        censorViews.forEach { windowManager.removeView(it) }
        censorViews.clear()
    }
}