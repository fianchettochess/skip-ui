// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import skip.foundation.UserDefaults
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AppStorageDefaultsRegressionTests {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("app-storage-${UUID.randomUUID()}", Context.MODE_PRIVATE)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun bridgeObservesFirstWriteAndRemovalWithoutPersistingDefault() {
        val prefs = preferences()
        val store = UserDefaults(platformValue = prefs)
        val storage = AppStorageSupport(value = "fallback", key = "name", store = store)
        assertEquals("fallback", storage.stringValue)
        assertFalse(prefs.contains("name"))
        prefs.edit().putString("other", "ignored").apply()
        idle()
        assertEquals("fallback", storage.stringValue)
        prefs.edit().putString("name", "external").apply()
        idle()
        assertEquals("external", storage.stringValue)
        prefs.edit().putBoolean("name", true).apply()
        idle()
        assertEquals("YES", storage.stringValue)
        prefs.edit().remove("name").apply()
        idle()
        assertEquals("fallback", storage.stringValue)
        assertFalse(prefs.contains("name"))
        storage.stringValue = "written"
        idle()
        assertEquals("written", prefs.getString("name", null))
        assertEquals("written", storage.stringValue)
    }

    @Test fun genericObservesFirstWriteAndRemovalWithoutPersistingDefault() {
        val prefs = preferences()
        val store = UserDefaults(platformValue = prefs)
        val storage = AppStorage(wrappedValue = "fallback", key = "name", store = store)
        assertEquals("fallback", storage.wrappedValue)
        assertFalse(prefs.contains("name"))
        prefs.edit().putString("other", "ignored").apply()
        idle()
        assertEquals("fallback", storage.wrappedValue)
        prefs.edit().putString("name", "external").apply()
        idle()
        assertEquals("external", storage.wrappedValue)
        prefs.edit().remove("name").apply()
        idle()
        assertEquals("fallback", storage.wrappedValue)
        assertFalse(prefs.contains("name"))
        storage.wrappedValue = "written"
        idle()
        assertEquals("written", prefs.getString("name", null))
        assertEquals("written", storage.wrappedValue)
    }

    @Test fun existingValuesAndBooleanDefaultsArePreserved() {
        val prefs = preferences()
        prefs.edit().putString("existing", "saved").apply()
        val store = UserDefaults(platformValue = prefs)
        val bridge = AppStorageSupport(value = "fallback", key = "existing", store = store)
        val generic = AppStorage(wrappedValue = "fallback", key = "existing", store = store)
        val boolBridge = AppStorageSupport(value = true, key = "flag1", store = store)
        val boolGeneric = AppStorage(wrappedValue = true, key = "flag2", store = store)
        assertEquals("saved", bridge.stringValue)
        assertEquals("saved", generic.wrappedValue)
        assertEquals(true, boolBridge.boolValue)
        assertEquals(true, boolGeneric.wrappedValue)
        assertFalse(prefs.contains("flag1"))
        assertFalse(prefs.contains("flag2"))
        prefs.edit().putBoolean("flag1", false).putBoolean("flag2", false).apply()
        idle()
        assertEquals(false, boolBridge.boolValue)
        assertEquals(false, boolGeneric.wrappedValue)
        prefs.edit().remove("flag1").remove("flag2").apply()
        idle()
        assertEquals(true, boolBridge.boolValue)
        assertEquals(true, boolGeneric.wrappedValue)
    }

    @Test fun genericSerializerReturnsToOriginalDefaultForRemovalAndInvalidValue() {
        val prefs = preferences()
        val store = UserDefaults(platformValue = prefs)
        val storage = AppStorage(wrappedValue = 7, key = "number", store = store,
            serializer = { value: Int -> "v:$value" },
            deserializer = { value: Any -> (value as? String)?.removePrefix("v:")?.toIntOrNull() })
        assertEquals(7, storage.wrappedValue)
        assertFalse(prefs.contains("number"))
        prefs.edit().putString("number", "v:11").apply()
        idle()
        assertEquals(11, storage.wrappedValue)
        prefs.edit().putString("number", "invalid").apply()
        idle()
        assertEquals(7, storage.wrappedValue)
        prefs.edit().remove("number").apply()
        idle()
        assertEquals(7, storage.wrappedValue)
        assertFalse(prefs.contains("number"))
        storage.wrappedValue = 13
        idle()
        assertEquals("v:13", prefs.getString("number", null))
        assertEquals(13, storage.wrappedValue)
    }
}
