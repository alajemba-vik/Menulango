package com.menulango.data.menu

import com.menulango.data.menu.local.Coordinates
import com.menulango.data.menu.local.Geohash
import com.menulango.data.menu.local.MenuCacheKey
import com.menulango.data.menu.local.Sha256
import com.menulango.data.menu.local.normalizeDishName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MenuCacheKeyTest {
    @Test
    fun samePrintedMenuGivesSameKeyWhateverTheOrderCaseOrAccents() {
        val firstPhoto = listOf("Μουσακάς", "Κοκορέτσι", "Crème brûlée")
        val secondPhoto = listOf("CRÈME BRULEE", "κοκορετσι", "μουσακας.", "Κοκορέτσι")

        assertEquals(MenuCacheKey.of(firstPhoto, null), MenuCacheKey.of(secondPhoto, null))
    }

    @Test
    fun differentMenusGiveDifferentKeys() {
        assertNotEquals(
            MenuCacheKey.of(listOf("Moussaka", "Kokoretsi"), null),
            MenuCacheKey.of(listOf("Moussaka", "Souvlaki"), null),
        )
    }

    @Test
    fun locationSeparatesIdenticalMenusInDifferentPlaces() {
        val names = listOf("Moussaka")
        val athens = MenuCacheKey.of(names, Coordinates(37.9715, 23.7267))
        val thessaloniki = MenuCacheKey.of(names, Coordinates(40.6401, 22.9444))

        assertEquals(athens.fingerprint, thessaloniki.fingerprint)
        assertNotEquals(athens.value, thessaloniki.value)
        assertEquals("~:${athens.fingerprint}", MenuCacheKey.of(names, null).value)
    }

    @Test
    fun normalisationFoldsAccentsAndPunctuation() {
        assertEquals("pastitsio", normalizeDishName("  Pastítsio!  "))
        assertEquals("χωριατικη σαλατα", normalizeDishName("Χωριάτικη   σαλάτα"))
        assertEquals("cafe", normalizeDishName("café"))
    }

    @Test
    fun geohashMatchesTheReferenceEncoding() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun sha256MatchesPublishedVectors() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray()),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(ByteArray(0)),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }
}
