package com.example.abxoverflow.droppedapk.fragment

import android.os.Bundle
import androidx.preference.Preference
import com.example.abxoverflow.droppedapk.Mods
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.preference.MaterialSeekbarPreference
import com.example.abxoverflow.droppedapk.preference.MaterialSwitchPreference

class SaturationFragment : BasePreferenceFragment() {

    private val saturation: MaterialSeekbarPreference by lazy { findPreference(getString(R.string.pref_key_display_color_saturation))!! }
    private val nativeMode: MaterialSwitchPreference by lazy { findPreference(getString(R.string.pref_key_display_color_native_mode))!! }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.saturation_preferences, rootKey)

        nativeMode.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            Mods.isDisplayNativeMode = !nativeMode.isChecked
            refresh()
            true
        }

        saturation.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            Mods.displaySaturation = saturation.getValue()
            refresh()
            true
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        nativeMode.isChecked = Mods.isDisplayNativeMode
        saturation.setValue(Mods.displaySaturation)
    }
}
