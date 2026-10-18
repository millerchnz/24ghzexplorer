# Google Play listing — 2.4 GHz Explorer

Paste each block into the Play Console field of the same name. Counts are characters, including spaces.

## Title

Limit: 30. Count: 16.

```
2.4 GHz Explorer
```

## Short description

Limit: 80. Count: 67.

```
Inspect Wi-Fi, Bluetooth, NFC and cells. Find a signal as you walk.
```

## Full description

Limit: 4000. Count: 3135.

```
2.4 GHz Explorer shows what the radios in your phone can actually see. Open a tab for Wi-Fi, Bluetooth, NFC or cellular, then tap a row. Every value that Android reported is listed. A value the platform left unset is a dash, so a missing reading never looks like zero.

Wi-Fi lists access points in the order they were first found. A later scan updates that row. Tap one for the SSID, BSSID, security, channel and information elements. Connect asks Android to join, and the system shows its own dialog. The password is not stored. Enterprise and WEP networks are handed to the system Wi-Fi screen.

Bluetooth lists BLE advertisements, classic inquiry and devices already paired with this phone. A row shows the name, an alias when one is set, and the maker when the advertisement says who it is, such as Apple or Samsung. Open a device for the address, bond, class, RSSI and advertisement bytes. Pair starts the system dialog, which is where you confirm. Discover services lists GATT characteristics and does not read or write them. Pairing requests are recorded, including a notification if one arrives while the app is in the background. The app does not accept or reject pairing.

NFC reads a card or tag held against the phone. You get the identifier, the technologies and any NDEF text or URI, plus the bytes in hex. Nothing is written to the tag. Mifare Classic sector contents are not read.

Radio lists the cells the modem will report, and the state of the Wi-Fi, Bluetooth and NFC radios in this phone. Neighbour cells are often withheld by the phone itself.

A row turns red when that device has not been heard for 10 seconds. The network this phone is joined to, this phone's own Bluetooth radio, and this phone's own radios stay the normal colour. Open a device and the page keeps refreshing while it is still heard, and shows the date and time it was last heard, down to the second. Give a device an alias and that name is what the list shows the next time the same device is seen.

Export shares one device, or every device on the current list, as a text file. When the phone has recorded them, the file includes the first and last place the device was heard and the strength at those places.

Map, on the Wi-Fi, Bluetooth and Radio lists, plots the devices that tab is hearing while you walk. Find device does the same for one signal. A device is drawn after three places, with a circle for how sure that point is. Gold is the device. Teal is you. The street map is OpenStreetMap and needs a network connection and a GPS fix. Outdoors, the result is tens of metres, not a point on a floor plan. NFC has no Find action, because a tag is only heard against the phone.

This is a reader. It does not unlock a device, crack a network, confirm a PIN, or send commands. An advertisement is how a device introduces itself. It is not a security audit of a lock.

Source available at: https://github.com/millerchnz/24ghzexplorer

Android 8.0 or newer. Precise location has to be on before Android will return Wi-Fi scans and cell identity. Bluetooth, NFC and phone-state permissions are asked on the tab that needs them. A short tour on first launch can be replayed from Settings with Show help.
```
