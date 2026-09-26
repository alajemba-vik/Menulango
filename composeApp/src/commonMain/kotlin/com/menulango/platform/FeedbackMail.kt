package com.menulango.platform

/**
 * A prefilled support email with app diagnostics only, never a diner's content or identity.
 *
 * @param issue what the diner ran into, when the report starts from a specific problem.
 */
internal expect fun feedbackMailUri(issue: String? = null): String

internal fun feedbackMailUri(
    version: String,
    platform: String,
    operatingSystem: String,
    issue: String? = null,
): String {
    val body =
        """
        MenuLango feedback

        What happened?

        ${issue?.let { "Issue: $it" }.orEmpty()}
        App version: $version
        Platform: $platform
        OS: $operatingSystem
        """.trimIndent()
    val subject = "MenuLango feedback".urlQueryComponent()
    return "mailto:menulango@gmail.com?subject=$subject&body=${body.urlQueryComponent()}"
}

private fun String.urlQueryComponent(): String =
    encodeToByteArray().joinToString(separator = "") { byte ->
        val value = byte.toInt() and 0xff
        when {
            value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code -> {
                value.toChar().toString()
            }

            value == '-'.code || value == '_'.code || value == '.'.code || value == '~'.code -> {
                value.toChar().toString()
            }

            else -> {
                "%" + value.toString(16).uppercase().padStart(2, '0')
            }
        }
    }
