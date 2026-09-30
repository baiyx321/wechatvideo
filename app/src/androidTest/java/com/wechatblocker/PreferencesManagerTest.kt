package com.wechatblocker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wechatblocker.data.PreferencesManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesManagerTest {
    
    private lateinit var prefsManager: PreferencesManager
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsManager = PreferencesManager(context)
    }
    
    @Test
    fun testDefaultValues() {
        assertEquals(50, prefsManager.minChars)
        assertEquals(10, prefsManager.cooldownMinutes)
        assertTrue(prefsManager.enabled)
    }
    
    @Test
    fun testSetAndGetValues() {
        prefsManager.minChars = 100
        assertEquals(100, prefsManager.minChars)
        
        prefsManager.cooldownMinutes = 20
        assertEquals(20, prefsManager.cooldownMinutes)
        
        prefsManager.enabled = false
        assertFalse(prefsManager.enabled)
    }
    
    @Test
    fun testLastPassTime() {
        val time = System.currentTimeMillis()
        prefsManager.lastPassTime = time
        assertEquals(time, prefsManager.lastPassTime)
    }
}
