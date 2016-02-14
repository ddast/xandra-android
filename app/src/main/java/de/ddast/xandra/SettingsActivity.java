/*
 * Copyright (C) 2016  Dennis Dast
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.ddast.xandra;

import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString(this.getString(R.string.pref_theme), "");
        if (theme.equals("dark")) {
            setTheme(R.style.DarkTheme);
        } else if (theme.equals("light")) {
            setTheme(R.style.LightTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            initSummary();
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                                 .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                                 .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(findPreference(key));
            if (key.equals(this.getString(R.string.pref_theme))) {
                ConnectActivity.themeHasChanged = true;
                getActivity().recreate();
            }
        }

        private void initSummary() {
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                updateSummary(getPreferenceScreen().getPreference(i));
            }
        }

        private void updateSummary(Preference pref) {
            if (pref instanceof ListPreference) {
                pref.setSummary(((ListPreference)pref).getEntry());
            } else if (pref instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) pref;
                if (pref.getSharedPreferences().getString(pref.getKey(), "").isEmpty()) {
                    pref.getEditor().remove(pref.getKey()).apply();
                    PreferenceManager.setDefaultValues(pref.getContext(), R.xml.preferences, true);
                    editTextPref.setText(pref.getSharedPreferences().getString(pref.getKey(), ""));
                }
                pref.setSummary(editTextPref.getText());
            }
        }
    }
}
