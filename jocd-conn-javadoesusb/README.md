# jOCD JavaDoesUSB Connection Module

This module provides a USB connection interface for jOCD using the JavaDoesUSB library.

## Overview

JavaDoesUSB is a modern Java USB library that leverages the Foreign Function & Memory API (FFM) introduced in JDK 22+ to provide direct access to native USB libraries without JNI. This integration is necessary because libusb (used by usb4java) has compatibility issues on macOS Sequoia, while JavaDoesUSB works reliably with JDK 23 and modern macOS versions.

## Requirements

- JDK 23 or higher
- JavaDoesUSB 1.2.1 (net.codecrete.usb:java-does-usb)

## Usage

Add the dependency to your project:

```gradle
dependencies {
    implementation 'br.org.certi:jocd-conn-javadoesusb:1.1.0'
}
```

Initialize the connection interface in your application:

```java
import br.org.certi.jocdconnjavadoesusb.JocdConnJavaDoesUsb;

public class Main {
    public static void main(String[] args) {
        // Initialize JavaDoesUSB connection interface
        JocdConnJavaDoesUsb.init();
        
        // Use jOCD as normal
        // ...
    }
}
```

## Building

Build the module using Gradle:

```bash
./gradlew build
```

This will compile the code, run tests, and publish to Maven local repository.

## Architecture

The module follows the same architectural pattern as existing jOCD connection modules:

- `JocdConnJavaDoesUsb`: Public API entry point with static `init()` method
- `JavaDoesUsbDevice`: Implementation of `ConnectionInterface` using JavaDoesUSB

## License

Copyright 2018 Fundação CERTI

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
