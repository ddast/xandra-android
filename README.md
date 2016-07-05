# xandra-android
Use your Android phone as keyboard and mouse for your PC.

Requires [xandra-server](https://github.com/ddast/xandra-server) to be run on
your PC.

## Screenshots

![Connect Activity](/img/xandra1.png?raw=true)
![Main Activity](/img/xandra2.png?raw=true)

## Connecting

1. Connect your PC and phone to your private LAN.
   Using xandra in an untrusted network environment (internet, LAN with
   untrusted parties) would be a horrible idea, since xandra uses neither
   authentication nor encryption.
2. Run xandra on your PC.
3. Run xandra on your phone.
   Enter the server hostname (xandra on your PC will print the hostname in case
   you don't know) or IP address and hit connect.

## Using xandra

* All text entered into the text field will be send to your PC.
  It does not matter how the text is entered, thus, all input methods such as
  typing, pasting or speech recognition should work.
* The … button toggles a special keys bar containing *Escape*, *Tab*, *Ctrl*,
  *Super*, *Alt*, arrow keys, volume keys, *Insert*, *Delete*, *Home*,
  *End*, *PageUp/Down* and function keys.
  The keys *Ctrl*, *Super* and *Alt* are usually used as modifier keys and
  therefore wait for the next character.
  So hitting *Ctrl* and then *q* will be interpreted as the combination
  *Ctrl+q*.
  To only type a modifier key without a second key, hit the button twice.
* The empty area beyond the text field is used to control the mouse and works
  similar to the touchpad of laptops (hint: hiding the keyboard will increase
  the space for mouse control).
  A short click is interpreted as a left mouse click and a long press as a
  right mouse click.
  Vertical scrolling works as a two-finger gesture (actually the maximum
  vertical movement of your two fingers is used, so it is possible to touch
  with the thumb of one hand and use one finger of the other hand for
  movement).

  
