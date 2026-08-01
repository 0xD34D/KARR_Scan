<h1 align="center">
<p>KARR Scan</p>
</h1>

![screenshot](https://github.com/0xD34D/KARR_Scan/raw/main/images/karr_scan.png "Scanning for devices")

### Disclaimer #1
---
This app was developed with the assistance of AI.  I've done my fair share of Android development
in the past, and I spent enough time figuring out how this all worked so I wanted a fast way to
test those findings.  The blabbering in this README, however, is mine and mine alone.

### Disclaimer #2
---
This app does not connect to KARR devices nor does it offer any way to interact or control these
devices.  It only scans for these devices broadcasting and parses the data they send out.  

### Disclaimer #3
---
And of course the obligatory legal statement that this software and related documentation are
provided strictly for educational, informational, and research purposes.

## What it does
KARR Scan passively scans for KARR Security devices and displays each device, and it's details, in a
list along with the total number of devices detected.  These devices are openly broadcasting this
information, KARR Scan simply parses it and displays it in a meaningful way.

## How It Works
KARR Security devices advertise themselves over Bluetooth, like countless other Bluetooth devices,
but they also broadcast 13 bytes of telemetry information.  Reverse engineering the official KARR
Security app showed how these bytes are interpreted.  See below for a breakdown of the bytes.

### 13-Byte Telemetry Payload Bit-Map
The 13-byte telemetry payload packs VIN characters, hardware configuration, battery voltage, GPS
coordinates, and security flags into bitfields to fit within advertising size constraints.


| Byte Offset | Bit Range | Field Name | Format / Encoding | Description & Mathematical Formula            |
|-------------|-----------|------------|-------------------|-----------------------------------------------|
| 0           | [0:5]     | VIN Char 1                  | 6-bit ASCII       | First character of 5-digit VIN  |
|             | [6:7]     | Unit Type (LSB)             | 2-bit UInt        | Lower 2 bits of Unit Type                     |
| 1           | [0:5]     | VIN Char 2                  | 6-bit ASCII       | Second character of 5-digit VIN |
|             | [6:7]     | Unit Type (MSB)             | 2-bit UInt        | Upper 2 bits of Unit Type                     |
| 2           | [0:5]     | VIN Char 3                  | 6-bit ASCII       | Third character of 5-digit VIN  |
|             | [6:7]     | Operating Mode (LSB)        | 2-bit UInt        | Lower 2 bits of Mode               |
| 3           | [0:5]     | VIN Char 4                  | 6-bit ASCII       | Fourth character of 5-digit VIN |
|             | [6]       | Operating Mode (MSB)        | 1-bit UInt        | Bit 2 of Mode                      |
|             | [7]       | Armed Flag                  | Boolean           | 1 = System Armed, 0 = Disarmed                |
| 4           | [0:5]     | VIN Char 5                  | 6-bit ASCII       | Fifth character of 5-digit VIN |
|             | [6]       | Snow Mode Flag              | Boolean           | 1 = Active, 0 = Inactive                      |
|             | [7]       | Elite Mode Flag             | Boolean           | 1 = Active, 0 = Inactive                     |
| 5           | [0:7]     | Battery Voltage             | 8-bit UInt        | Battery Voltage (mV)                          |
| 6-8         | [0:23]    | Lattitude                   | 24-bit LE UInt    | Little-Endian 24-bit encoded latitude         |
| 9-11        | [0:23]    | Longitude                   | 24-bit LE UInt    | Little-Endian 24-bit encoded longitude        |
| 12          | [1]       | Encryption Flag             | Boolean           | 1 = Encrypted Payload, 0 = Not Encrypted      |

***Note:***
*There is an alternate 26 byte version of the data where the first 13 bytes are used for the device
name, with the remainder being identical to the above table but offset by 13*

