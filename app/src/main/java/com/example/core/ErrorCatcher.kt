package com.example.core

/**
 * The reusable PackFlow error catcher (JS + Android), injected into every generated app.
 * Canonical sources live in reusable/error-catcher/.
 */
object ErrorCatcher {
    const val BRIDGE_NAME = "PackFlowAndroid"
    const val MARKER = "PackFlow error catcher v1"
    const val JS: String = ErrorCatcherAssets.JS
    const val ANDROID_FILE_NAME = "PackFlowCrashCatcher.kt"
    private const val TEMPLATE_PACKAGE = "package com.example.yourapp"

    val scriptTag: String get() = "<script>\n$JS</script>"

    /** Puts the catcher script first in <head> so it sees errors from every later script. */
    fun injectIntoHtml(html: String): String {
        if (html.contains(MARKER)) return html
        val tag = scriptTag
        val head = Regex("(?i)<head(\\s[^>]*)?>").find(html)
        if (head != null) {
            val at = head.range.last + 1
            return html.substring(0, at) + "\n" + tag + "\n" + html.substring(at)
        }
        val htmlTag = Regex("(?i)<html(\\s[^>]*)?>").find(html)
        if (htmlTag != null) {
            val at = htmlTag.range.last + 1
            return html.substring(0, at) + "\n<head>\n" + tag + "\n</head>" + html.substring(at)
        }
        val doctype = Regex("(?i)^\\s*<!doctype[^>]*>").find(html)
        if (doctype != null) {
            val at = doctype.range.last + 1
            return html.substring(0, at) + "\n" + tag + html.substring(at)
        }
        return tag + "\n" + html
    }

    /** Android crash catcher source for a generated app's package. */
    fun androidKotlin(packageName: String): String =
        ErrorCatcherAssets.ANDROID_KT_TEMPLATE.replaceFirst(TEMPLATE_PACKAGE, "package $packageName")
}
