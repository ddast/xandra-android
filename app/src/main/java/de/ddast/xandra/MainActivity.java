/*
 * Copyright (C) 2017  Dennis Dast
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;

public class MainActivity extends AppCompatActivity implements TcpClientObserver {
    private static final String TAG    = "MainActivity";
    private static final boolean DEBUG = false;

    private int mPort;
    private long mTapdelay;
    private float mTaptol, mSensitivity, mAcceleration, mScrollThreshold;
    private int mSpecialKeysVisibility = View.GONE;
    private NoCursorEditText mBufferEdit;
    private HorizontalScrollView mLayoutKeys;
    private Button mToggleButton;
    private String mServerAddr;
    private TcpClient mTcpClient;
    private MouseGestureWatcher mMouseGestureWatcher;
    private SendCharsTextWatcher mSendCharsTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString(this.getString(R.string.pref_theme), "");
        if (theme.equals("dark")) {
            setTheme(R.style.DarkThemeNoActionbar);
        } else if (theme.equals("light")) {
            setTheme(R.style.LightThemeNoActionbar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (sharedPreferences.getBoolean(this.getString(R.string.pref_lock), false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        mPort            = Integer.valueOf(sharedPreferences.getString(
                                           this.getString(R.string.pref_port), ""));
        mTapdelay        = Long.valueOf(sharedPreferences.getString(
                                        this.getString(R.string.pref_tapdelay), ""));
        mTaptol          = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_taptol), ""));
        mSensitivity     = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_sensitivity), ""));
        mAcceleration    = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_acceleration), ""));
        mScrollThreshold = Float.valueOf(sharedPreferences.getString(
                                         this.getString(R.string.pref_scrollthreshold), ""));
        mServerAddr      = getIntent().getStringExtra(ConnectActivity.SERVERADDR);

        initViews();
        setUiToDisconnected();
    }

    private void initViews() {
        mBufferEdit = (NoCursorEditText)findViewById(R.id.buffer_edit);
        mBufferEdit.setHorizontallyScrolling(true);  // does not work in xml for some reason

        mLayoutKeys = (HorizontalScrollView)findViewById(R.id.layout_keys);
        mToggleButton = (Button)findViewById(R.id.button_togglekeys);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpecialKeysVisibility = mLayoutKeys.getVisibility() == View.VISIBLE ? View.GONE :
                                                                                       View.VISIBLE;
                mLayoutKeys.setVisibility(mSpecialKeysVisibility);
            }
        });

        int[] buttonIds = {R.id.button_esc, R.id.button_tab, R.id.button_ctrl, R.id.button_sup,
                           R.id.button_alt, R.id.button_left, R.id.button_down, R.id.button_up,
                           R.id.button_right, R.id.button_voldn, R.id.button_volup,
                           R.id.button_voltog, R.id.button_mid, R.id.button_ins, R.id.button_del,
                           R.id.button_home, R.id.button_end, R.id.button_pgup, R.id.button_pgdn,
                           R.id.button_f1, R.id.button_f2, R.id.button_f3, R.id.button_f4,
                           R.id.button_f5, R.id.button_f6, R.id.button_f7, R.id.button_f8,
                           R.id.button_f9, R.id.button_f10, R.id.button_f11, R.id.button_f12};
        byte[] buttonCodes = {TcpClient.ESCAPE, TcpClient.TAB, TcpClient.CTRL, TcpClient.SUP,
                              TcpClient.ALT, TcpClient.LEFT, TcpClient.DOWN, TcpClient.UP,
                              TcpClient.RIGHT, TcpClient.VOLDN, TcpClient.VOLUP,
                              TcpClient.VOLTOG, TcpClient.MIDDLECLICK, TcpClient.INS, TcpClient.DEL,
                              TcpClient.HOME, TcpClient.END, TcpClient.PGUP, TcpClient.PGDN,
                              TcpClient.F1, TcpClient.F2, TcpClient.F3, TcpClient.F4,
                              TcpClient.F5, TcpClient.F6, TcpClient.F7, TcpClient.F8,
                              TcpClient.F9, TcpClient.F10, TcpClient.F11, TcpClient.F12};

        for (int i = 0; i < buttonIds.length; ++i) {
            Button button = (Button)findViewById(buttonIds[i]);
            final byte code = buttonCodes[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTcpClient.sendSpecialKey(code);
                }
            });
        }
    }

    public void connectionEstablished() {
        if (DEBUG) {
            Log.d(TAG, "Connection established");
        }
        setUiToConnected();
    }

    public void connectionLost() {
        if (DEBUG) {
            Log.d(TAG, "Connection lost");
        }
        setUiToDisconnected();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (mTcpClient.isConnected() && mMouseGestureWatcher != null) {
            return mMouseGestureWatcher.processTouchEvent(event) || super.onTouchEvent(event);
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onPause() {
        if (DEBUG) {
            Log.d(TAG, "Disconnecting due to onPause()");
        }
        super.onPause();
        mTcpClient.disconnect();
        mBufferEdit.removeTextChangedListener(mSendCharsTextWatcher);
        mTcpClient = null;
        mSendCharsTextWatcher = null;
        mMouseGestureWatcher = null;
    }

    @Override
    protected void onResume() {
        if (DEBUG) {
            Log.d(TAG, "Connect due to onResume()");
        }
        super.onResume();
        mTcpClient = new TcpClient(mServerAddr, mPort, this);
        mMouseGestureWatcher = new MouseGestureWatcher(mTcpClient, mTapdelay, mTaptol, mSensitivity,
                                                       mAcceleration, mScrollThreshold);
        mSendCharsTextWatcher = new SendCharsTextWatcher(mTcpClient);
        mBufferEdit.addTextChangedListener(mSendCharsTextWatcher);
        mTcpClient.connect();
    }

    private void setUiToDisconnected() {
        mBufferEdit.setEnabled(false);
        mBufferEdit.setText(R.string.notconnected);
        mLayoutKeys.setVisibility(View.GONE);
        mToggleButton.setEnabled(false);
    }

    private void setUiToConnected() {
        mBufferEdit.setEnabled(true);
        mBufferEdit.setText(" ");
        showSoftKeyboard(mBufferEdit);
        mToggleButton.setEnabled(true);
    }

    private void showSoftKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
        if (mSpecialKeysVisibility == View.VISIBLE) {
            mLayoutKeys = (HorizontalScrollView) findViewById(R.id.layout_keys);
            mLayoutKeys.setVisibility(View.VISIBLE);
        }
    }
}