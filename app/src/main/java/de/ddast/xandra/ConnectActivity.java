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

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ConnectActivity extends AppCompatActivity {

    public final static String SERVERADDR = "serveraddr";
    public static boolean themeHasChanged = false;

    private EditText mServerEdit;
    private Button mConnectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString(this.getString(R.string.pref_theme), "");
        if (theme.equals("dark")) {
            setTheme(R.style.DarkTheme);
        } else if (theme.equals("light")) {
            setTheme(R.style.LightTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        mServerEdit = (EditText)findViewById(R.id.server_edit);
        mConnectButton = (Button)findViewById(R.id.connect_button);

        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String serverAddr = settings.getString(SERVERADDR, "");
        mServerEdit.setText(serverAddr);

        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ConnectActivity.this, MainActivity.class);
                i.putExtra(SERVERADDR, mServerEdit.getText().toString());
                startActivity(i);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(SERVERADDR, mServerEdit.getText().toString());
        editor.apply();
    }

    @Override
    protected void onResume() {
        if (themeHasChanged) {
            themeHasChanged = false;
            recreate();
        }
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString(this.getString(R.string.pref_theme), "");
        if (theme.equals("dark")) {
            menu.findItem(R.id.action_settings).setIcon(R.drawable.ic_settings_white_24dp);
        } else if (theme.equals("light")) {
            menu.findItem(R.id.action_settings).setIcon(R.drawable.ic_settings_black_24dp);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
