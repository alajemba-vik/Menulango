package com.menulango.data.menu.local

/**
 * Identifies a menu by what is written on it, not by the photograph of it.
 *
 * Exists because two photos of the same menu differ in every pixel, but their dish lists are
 * identical. The key is a hash of the printed dish names — sorted, lowercased, accents folded —
 * combined with a coarse geohash when a location is known. It is stable across photographers,
 * which is what lets the same key later move to a shared server-side cache without changing any
 * caller.
 */
internal data class MenuCacheKey(
    val fingerprint: String,
    val geohash: String?,
) {
    /** The single string stored as the primary key. */
    val value: String get() = "${geohash ?: NO_LOCATION}:$fingerprint"

    companion object {
        private const val NO_LOCATION = "~"

        /** Geohash precision 6 is about 1.2 km × 0.6 km: one neighbourhood, not one address. */
        const val GEOHASH_PRECISION: Int = 6

        fun of(
            printedDishNames: List<String>,
            location: Coordinates?,
        ): MenuCacheKey {
            val canonical =
                printedDishNames
                    .map(::normalizeDishName)
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
                    .joinToString(separator = "\n")
            return MenuCacheKey(
                fingerprint = Sha256.hex(canonical.encodeToByteArray()),
                geohash = location?.let { Geohash.encode(it.latitude, it.longitude, GEOHASH_PRECISION) },
            )
        }
    }
}

/** A position, reduced to what the cache key needs. */
internal data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Reduces a printed dish name to the form two photographs of the same menu would agree on:
 * lowercase, accents removed, punctuation dropped, whitespace collapsed.
 */
internal fun normalizeDishName(printed: String): String {
    val folded = StringBuilder(printed.length)
    for (char in printed.lowercase()) {
        if (char in COMBINING_MARKS) continue
        val base = ACCENT_FOLDS[char] ?: char
        folded.append(if (base.isLetterOrDigit()) base else ' ')
    }
    return folded.toString().trim().replace(WHITESPACE, " ")
}

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = '\u0300'..'\u036F'

private val ACCENT_FOLDS: Map<Char, Char> =
    buildMap {
        fun fold(
            accented: String,
            base: Char,
        ) = accented.forEach { put(it, base) }
        // Latin
        fold("àáâãäåāăą", 'a')
        fold("çćĉċč", 'c')
        fold("ďđ", 'd')
        fold("èéêëēĕėęě", 'e')
        fold("ĝğġģ", 'g')
        fold("ĥħ", 'h')
        fold("ìíîïĩīĭįı", 'i')
        fold("ĵ", 'j')
        fold("ķ", 'k')
        fold("ĺļľŀł", 'l')
        fold("ñńņňŉ", 'n')
        fold("òóôõöøōŏő", 'o')
        fold("ŕŗř", 'r')
        fold("śŝşšș", 's')
        fold("ţťŧț", 't')
        fold("ùúûüũūŭůűų", 'u')
        fold("ŵ", 'w')
        fold("ýÿŷ", 'y')
        fold("źżž", 'z')
        // Greek: tonos and dialytika, and final sigma
        fold("ά", 'α')
        fold("έ", 'ε')
        fold("ή", 'η')
        fold("ίϊΐ", 'ι')
        fold("ό", 'ο')
        fold("ύϋΰ", 'υ')
        fold("ώ", 'ω')
        fold("ς", 'σ')
    }

/**
 * Standard geohash encoding.
 *
 * Coarse on purpose — it separates "the taverna on this street" from "a taverna in another city"
 * without recording where anyone actually stood.
 */
internal object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(
        latitude: Double,
        longitude: Double,
        precision: Int,
    ): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "not a coordinate: $latitude,$longitude" }
        var latRange = -90.0 to 90.0
        var lonRange = -180.0 to 180.0
        val hash = StringBuilder(precision)
        var bits = 0
        var bitCount = 0
        var evenBit = true
        while (hash.length < precision) {
            if (evenBit) {
                val mid = (lonRange.first + lonRange.second) / 2
                bits = bits shl 1
                if (longitude >= mid) {
                    bits = bits or 1
                    lonRange = mid to lonRange.second
                } else {
                    lonRange = lonRange.first to mid
                }
            } else {
                val mid = (latRange.first + latRange.second) / 2
                bits = bits shl 1
                if (latitude >= mid) {
                    bits = bits or 1
                    latRange = mid to latRange.second
                } else {
                    latRange = latRange.first to mid
                }
            }
            evenBit = !evenBit
            if (++bitCount == 5) {
                hash.append(BASE32[bits])
                bits = 0
                bitCount = 0
            }
        }
        return hash.toString()
    }
}

/**
 * SHA-256, because the Kotlin standard library has no multiplatform digest and a cache key that
 * will one day be shared between strangers should not collide.
 */
internal object Sha256 {
    private val K: IntArray =
        listOf(
            "428a2f98",
            "71374491",
            "b5c0fbcf",
            "e9b5dba5",
            "3956c25b",
            "59f111f1",
            "923f82a4",
            "ab1c5ed5",
            "d807aa98",
            "12835b01",
            "243185be",
            "550c7dc3",
            "72be5d74",
            "80deb1fe",
            "9bdc06a7",
            "c19bf174",
            "e49b69c1",
            "efbe4786",
            "0fc19dc6",
            "240ca1cc",
            "2de92c6f",
            "4a7484aa",
            "5cb0a9dc",
            "76f988da",
            "983e5152",
            "a831c66d",
            "b00327c8",
            "bf597fc7",
            "c6e00bf3",
            "d5a79147",
            "06ca6351",
            "14292967",
            "27b70a85",
            "2e1b2138",
            "4d2c6dfc",
            "53380d13",
            "650a7354",
            "766a0abb",
            "81c2c92e",
            "92722c85",
            "a2bfe8a1",
            "a81a664b",
            "c24b8b70",
            "c76c51a3",
            "d192e819",
            "d6990624",
            "f40e3585",
            "106aa070",
            "19a4c116",
            "1e376c08",
            "2748774c",
            "34b0bcb5",
            "391c0cb3",
            "4ed8aa4a",
            "5b9cca4f",
            "682e6ff3",
            "748f82ee",
            "78a5636f",
            "84c87814",
            "8cc70208",
            "90befffa",
            "a4506ceb",
            "bef9a3f7",
            "c67178f2",
        ).map { it.toLong(16).toInt() }.toIntArray()

    private val INITIAL_HASH: IntArray =
        listOf(
            "6a09e667",
            "bb67ae85",
            "3c6ef372",
            "a54ff53a",
            "510e527f",
            "9b05688c",
            "1f83d9ab",
            "5be0cd19",
        ).map { it.toLong(16).toInt() }.toIntArray()

    fun hex(message: ByteArray): String =
        digest(message).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    fun digest(message: ByteArray): ByteArray {
        val h = INITIAL_HASH.copyOf()
        val bitLength = message.size.toLong() * 8
        val paddedSize = ((message.size + 9 + 63) / 64) * 64
        val padded = message.copyOf(paddedSize)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[paddedSize - 1 - i] = (bitLength ushr (8 * i)).toByte()

        val w = IntArray(64)
        for (block in 0 until paddedSize step 64) {
            for (t in 0 until 16) {
                val o = block + t * 4
                w[t] = (padded[o].toInt() and 0xff shl 24) or (padded[o + 1].toInt() and 0xff shl 16) or
                    (padded[o + 2].toInt() and 0xff shl 8) or (padded[o + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }
}
