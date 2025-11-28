# stlinkflashtool

Command-line interface example for testing jOCD with JavaDoesUSB backend.

## Overview

This example demonstrates how to use jOCD with the JavaDoesUSB connection interface to enumerate and program CMSIS-DAP devices. JavaDoesUSB is a modern Java USB library that uses the Foreign Function & Memory (FFM) API introduced in JDK 22+ to provide direct access to native USB libraries without JNI. It seems that libusb on macOS Sequoia is broken, hence why we ended up here

## Requirements

- **JDK 23 or higher**: Required for FFM API support
- **CMSIS-DAP compatible device**: Any ARM Cortex-M development board with CMSIS-DAP debug interface
- **macOS Sequoia**: JavaDoesUSB seems to works reliably on Sequoia

## Building

To build the example application:

```bash
./gradlew buildAll
```

This will:
1. Build the jocd-conn-javadoesusb module (and its dependencies)
2. Build the example application
3. Copy all dependencies to `build/libs/`

## Running

### List Connected Devices

To list all connected CMSIS-DAP devices:

```bash
java -cp "build/libs/*" br.org.certi.stlinkflashtool.MainClass --list
```

To suppress FFM API warnings (optional):

```bash
java --enable-native-access=ALL-UNNAMED -cp "build/libs/*" br.org.certi.stlinkflashtool.MainClass --list
```

Example output:
```
[1]: FRDM-K64F (0240000032044e4500257009997b00386781000097969900)
[2]: BBC micro:bit (9900023431864e45002f1012000000230000000097969900)
```

### Flash a Device

To flash the first connected device with a hex file:

```bash
java -cp "build/libs/*" br.org.certi.stlinkflashtool.MainClass firmware.hex
```

To flash a specific device by board ID:

```bash
java -cp "build/libs/*" br.org.certi.stlinkflashtool.MainClass --boardid 0240000032044e4500257009997b00386781000097969900 firmware.hex
```

### Help

To display usage information:

```bash
java -cp "build/libs/*" br.org.certi.stlinkflashtool.MainClass --help
```

## Usage

```
Usage:
javaflashtool [OPTIONS] [FILE]

	Options:
	--help (-h): show this help message.
	--list (-l): list all the CMSIS-DAP connected devices.
	--boardid ID (-bid): flash the board with the chosen ID.
```

## Troubleshooting

### Permission Issues

On Linux, you may need to add udev rules to access USB devices without root privileges:

```bash
# Create udev rule file
sudo nano /etc/udev/rules.d/99-cmsis-dap.rules

# Add the following line (adjust VID/PID as needed):
SUBSYSTEM=="usb", ATTR{idVendor}=="0d28", ATTR{idProduct}=="0204", MODE="0666"

# Reload udev rules
sudo udevadm control --reload-rules
sudo udevadm trigger
```

### No Devices Found

If no devices are found:
1. Verify the device is connected and powered on
2. Check that the device appears in system USB device list
3. Ensure you have proper USB permissions (see above)
4. Try a different USB cable or port

### JDK Version

Ensure you're using JDK 23 or higher:

```bash
java -version
```

If you see a version lower than 23, update your JDK installation.

## Differences from usb4java Backend

This example is functionally identical to the usb4java example but uses the JavaDoesUSB backend instead:

- **Initialization**: Uses `JocdConnJavaDoesUsb.init()` instead of `JocdConnUsb4Java.init()`
- **JDK Requirement**: Requires JDK 23+ (usb4java works with older JDK versions)
- **Platform Support**: Better compatibility with macOS Sequoia and modern operating systems
- **Performance**: Similar performance characteristics to usb4java

## License

Copyright 2018 Fundação CERTI

Licensed under the Apache License, Version 2.0. See LICENSE.txt for details.
