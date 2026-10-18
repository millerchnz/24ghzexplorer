# 2.4 GHz Explorer

Android tool for reading the Wi-Fi, Bluetooth, NFC and cellular APIs. Each radio has a list. Tap a row and every value that API returned is shown. A value the platform did not report is a dash, so a missing reading never looks like zero.

Connect and pair use Android's own prompts. The app does not confirm a PIN, write to an NFC tag, or send commands to a device. Discovering Bluetooth services lists characteristics and does not read or write them. An advertisement is how a device introduces itself. It is not a security audit of a lock.

## Requirements

- Android 8.0 or newer (API 26)
- Precise location and system Location switched on for Wi-Fi scan results and cell identity
- Nearby Wi-Fi devices permission on Android 13 and newer
- Bluetooth scan and connect for the Bluetooth tab
- NFC hardware, switched on, for the NFC tab
- Phone state for the cellular network name. Signal strength still shows if it is denied; cell identity may then be blank
- Internet, for the street maps

The first launch starts a short tour. It can be replayed from Settings with **Show help**.

## Wi-Fi

Scan lists access points in the order they were first discovered. A later scan updates a row in place. A new access point is added at the bottom. One that has gone is dropped. The list is not sorted by signal strength.

Tap a row for the scan fields, including information elements. **Connect** asks Android to join, and the system shows its own confirm dialog. The password is not stored. The link is for this app until you disconnect. Enterprise and WEP networks are sent to the system Wi-Fi screen.

Android throttles foreground scans to a few every two minutes. The app asks again every 30 seconds while a scan is left running, and pauses the scan when the app is in the background. A row turns red when that access point has not been heard for 10 seconds. The network this phone is using stays the normal colour.

## Bluetooth

**Scan** clears the previous list, puts paired devices back, then fills in what BLE advertisements and classic inquiry hear. Rows stay in discovery order. A row turns red when that device has not been heard for 10 seconds, including a bonded device this scan has not heard. This phone's own radio stays the normal colour.

Tap a device for its address, name, bond, class, RSSI and advertisement bytes. **Connect** opens a link and does not read data. **Pair** starts the system dialog, which is where you confirm. Classic inquiry pauses for 30 seconds after Pair, because discovery cancels bonding.

**Discover services** connects, lists services, characteristics and descriptors, then disconnects.

The pairing card records pairing requests and ACL connections while this process is alive. A pairing request that arrives with the app in the background also posts a notification. The app does not accept or reject the request. This phone's own Bluetooth address is redacted by Android, and many BLE addresses rotate.

## NFC

Reader mode is on while the NFC tab is open. Hold a card or tag against the phone. The list keeps the tags read in this session.

NDEF text and URI are decoded from the payload. The same bytes are shown in full as hex. NDEF itself is not encrypted. Mifare Classic blocks stay unread, because those blocks need a key and this app does not present one. Nothing is written to the tag.

## Radio

**Refresh** asks the modem for the cells it will report, and adds a row for the Wi-Fi, Bluetooth and NFC radios in this phone. A cell that was already on the list keeps its place. A cell turns red when its report is more than 10 seconds old. This phone's own radios stay the normal colour. Neighbour cells are often withheld by the phone.

## Device details

An open device refreshes every 2 seconds while it is still heard. The page stays where you scrolled, and the top of the page shows the date and time it was last heard, down to the second. If it has not been heard for 10 seconds, the text turns red and the last reading stays. A later sighting updates the page again. Long-press a line to copy it.

**Export** on a device shares that device as a text file. **Export** on a list shares every device currently on that page. Each device includes the first and last place it was heard, and the strength at those places, once the phone has recorded them. A Wi-Fi password is never included.

## List map

**Map**, beside **Export** on the Wi-Fi, Bluetooth and Radio lists, is a live view of that tab. It samples every device the radio is hearing while the map is open. A device is drawn after three places. A fourth is kept, and a later place replaces the oldest. The device leaves as soon as those samples stop. Gold marks are devices. The teal mark is you. Tap a marker for the fields from the latest sample. This is not a record of earlier Find walks.

## Find device

Wi-Fi, Bluetooth and cell pages have **Find device**. It opens a map, marks where you are, and drops the device where the signal has been strongest, with a circle for how sure that point is.

The device is drawn after three places. The newest four are kept, and a later place replaces the oldest. A walk along only one side of the device leaves a large circle. Outdoors, with samples on more than one side, the result is still tens of metres, not a point on a floor plan. Gold is the device. Teal is you.

The street map is OpenStreetMap via osmdroid and needs a network connection. GPS has to have a fix. Wi-Fi signal updates only as fast as Android returns scans. NFC has no Find action, because a tag is only heard against the phone.

## Build

```bash
./gradlew installDebug
```

Package: `com.millerchnz.ghzexplorer`

SDK path is read from `local.properties` (`sdk.dir`), which is not committed. The Gradle build needs a JDK. The project compiles as Java 11 bytecode.

```bash
./gradlew testDebugUnitTest
```

## Stack

- Kotlin, Jetpack Compose, Material 3, navigation-compose
- Scan results kept in memory for the process. Pairing events kept in SharedPreferences (`ghz_explorer`), excluded from backup
- osmdroid for the Find device map. No Google Maps key
- `WifiManager` scans and `WifiNetworkSpecifier` for an app-scoped connect on Android 10 and newer
- `BluetoothLeScanner` plus classic discovery, `createBond` for pairing, and GATT only to list services
- `NfcAdapter` reader mode
- `TelephonyManager` cell info
- `LocationManager` for the walk on the Find map
