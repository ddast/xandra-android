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

class SendCharsTextWatcher implements TextWatcher {
    private boolean ignore = false;
    private TcpClient mTcpClient;

    SendCharsTextWatcher(TcpClient tcpClient) {
        mTcpClient = tcpClient;
    }

    @Override
    public void afterTextChanged(Editable s) {
        // s must not be empty.  It always contains one space to detect further backspace input.
        if (s.toString().contains("\n") || s.length() == 0) {
            ignore = true;
            s.replace(0, s.length(), " ", 0, 1);
        }
    }

    @Override
    public void beforeTextChanged (CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged (CharSequence s, int start, int before, int count) {
        if (ignore) {
            ignore = false;
            return;
        }
        if (before == 0) {
            mTcpClient.sendUTF8(s.subSequence(start, start + count).toString());
        } else if (count == 0) {
            for (int i = 0; i < before; ++i) {
                mTcpClient.sendSpecialKey(TcpClient.BACKSPACE);
            }
        }
    }
}