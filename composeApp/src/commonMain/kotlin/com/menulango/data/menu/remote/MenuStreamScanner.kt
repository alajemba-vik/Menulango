package com.menulango.data.menu.remote

/**
 * A piece of the response that is complete enough to parse on its own.
 */
internal sealed interface StreamFragment {
    val json: String

    data class MetaBlock(
        override val json: String,
    ) : StreamFragment

    data class DishObject(
        override val json: String,
    ) : StreamFragment
}

/**
 * Finds complete dish objects inside a response that is still arriving.
 *
 * Exists because speed is a feature: the model writes a thirty-dish menu over several seconds,
 * and the diner should see the first dish the moment it is finished, not after the last. The
 * scanner tracks just enough JSON structure — string state, nesting depth, and the last key seen
 * at the root — to cut out each element of `dishes` and the `menu` block as soon as it closes.
 *
 * It works on raw bytes rather than text so a multi-byte character split across two network
 * chunks can never be decoded in half: every structural character in JSON is ASCII, and ASCII
 * bytes never occur inside a UTF-8 multi-byte sequence.
 */
internal class MenuStreamScanner {
    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0
    private var cursor = 0

    private var depth = 0
    private var inString = false
    private var escaped = false
    private var stringStart = -1
    private var lastRootString: String? = null
    private var inDishesArray = false

    private var captureStart = -1
    private var captureDepth = -1
    private var capturingMeta = false

    /** True once the root object has closed, meaning the response arrived in full. */
    var isComplete: Boolean = false
        private set

    fun feed(chunk: ByteArray): List<StreamFragment> {
        append(chunk)
        val fragments = mutableListOf<StreamFragment>()
        while (cursor < size) {
            scan(buffer[cursor], cursor)?.let(fragments::add)
            cursor++
        }
        return fragments
    }

    private fun scan(
        byte: Byte,
        index: Int,
    ): StreamFragment? {
        if (inString) {
            when {
                escaped -> {
                    escaped = false
                }

                byte == BACKSLASH -> {
                    escaped = true
                }

                byte == QUOTE -> {
                    inString = false
                    if (depth == 1) lastRootString = buffer.decodeToString(stringStart + 1, index)
                }
            }
            return null
        }
        when (byte) {
            QUOTE -> {
                inString = true
                stringStart = index
            }

            OPEN_BRACE, OPEN_BRACKET -> {
                if (depth == 1 && byte == OPEN_BRACE && lastRootString == KEY_MENU) startCapture(index, meta = true)
                if (depth == 1 && byte == OPEN_BRACKET && lastRootString == KEY_DISHES) inDishesArray = true
                if (depth == 2 && byte == OPEN_BRACE && inDishesArray) startCapture(index, meta = false)
                depth++
            }

            CLOSE_BRACE, CLOSE_BRACKET -> {
                depth--
                if (depth == 1 && byte == CLOSE_BRACKET) inDishesArray = false
                if (depth == 0) isComplete = true
                if (captureStart >= 0 && depth == captureDepth && byte == CLOSE_BRACE) return endCapture(index)
            }
        }
        return null
    }

    private fun startCapture(
        index: Int,
        meta: Boolean,
    ) {
        captureStart = index
        captureDepth = depth
        capturingMeta = meta
    }

    private fun endCapture(index: Int): StreamFragment {
        val json = buffer.decodeToString(captureStart, index + 1)
        captureStart = -1
        return if (capturingMeta) StreamFragment.MetaBlock(json) else StreamFragment.DishObject(json)
    }

    private fun append(chunk: ByteArray) {
        if (size + chunk.size > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, size + chunk.size))
        }
        chunk.copyInto(buffer, destinationOffset = size)
        size += chunk.size
    }

    private companion object {
        const val INITIAL_CAPACITY = 16 * 1024
        const val KEY_MENU = "menu"
        const val KEY_DISHES = "dishes"
        const val QUOTE = '"'.code.toByte()
        const val BACKSLASH = '\\'.code.toByte()
        const val OPEN_BRACE = '{'.code.toByte()
        const val CLOSE_BRACE = '}'.code.toByte()
        const val OPEN_BRACKET = '['.code.toByte()
        const val CLOSE_BRACKET = ']'.code.toByte()
    }
}
