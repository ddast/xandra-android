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

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

class SendCharsTextWatcher implements TextWatcher {
    private static final String TAG    = "SendCharsTextWatcher";
    private static final boolean DEBUG = false;

    private boolean ignore = false;
    private CharSequence textBefore;
    private TcpClient mTcpClient;

    private class TextChangedInfo {
        TextChangedInfo(int start, int before, int count) {
            this.start = start;
            this.before = before;
            this.count = count;
        }
        int start, before, count;
    }

    SendCharsTextWatcher(TcpClient tcpClient) {
        mTcpClient = tcpClient;
    }

    private void getActualChanges(CharSequence s, TextChangedInfo changeInfo) {
        int lastIndex = changeInfo.start+Math.min(changeInfo.before, changeInfo.count);
        for (int i = changeInfo.start; i < lastIndex; ++i) {
            if (s.charAt(i) == textBefore.charAt(i)) {
                --changeInfo.before;
                --changeInfo.count;
                ++changeInfo.start;
            } else {
                break;
            }
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (DEBUG) {
            Log.d(TAG, "afterTextChanged() " + s.toString());
        }
        // s must not be empty.  It always contains one space to detect further backspace input.
        if (s.toString().contains("\n") || s.length() == 0) {
            ignore = true;
            s.replace(0, s.length(), " ", 0, 1);
        }
    }

    @Override
    public void beforeTextChanged (CharSequence s, int start, int count, int after) {
        if (DEBUG) {
            Log.d(TAG, "beforeTextChanged() "
                    + s.toString() + " "
                    + String.valueOf(start) + " "
                    + String.valueOf(count) + " "
                    + String.valueOf(after));
        }
        textBefore = s;
    }

    @Override
    public void onTextChanged (CharSequence s, int start, int before, int count) {
        if (DEBUG) {
            Log.d(TAG, "onTextChanged() "
                    + s.toString() + " "
                    + String.valueOf(start) + " "
                    + String.valueOf(before) + " "
                    + String.valueOf(count));
        }
        if (ignore) {
            ignore = false;
            return;
        }
        TextChangedInfo tci = new TextChangedInfo(start, before, count);
        getActualChanges(s, tci);
        if (DEBUG) {
            Log.d(TAG, "actualChanges "
                    + s.toString() + " "
                    + String.valueOf(tci.start) + " "
                    + String.valueOf(tci.before) + " "
                    + String.valueOf(tci.count));
        }
        for (int i = 0; i < tci.before; ++i) {
            mTcpClient.sendSpecialKey(TcpClient.BACKSPACE);
        }
        if (tci.count > 0) {
            mTcpClient.sendUTF8(s.subSequence(tci.start, tci.start + tci.count).toString());
        }
    }
}