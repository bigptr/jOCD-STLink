/*
 * Copyright 2018 Fundação CERTI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package br.org.certi.jocdconnjavadoesusb;

import br.org.certi.jocd.Jocd;
import br.org.certi.jocdconnjavadoesusb.connectioninterface.JavaDoesUsbDevice;

/**
 * JocdConnJavaDoesUsb is a companion library for jOCD that provides access to USB devices via the
 * JavaDoesUSB library. JavaDoesUSB leverages the Foreign Function & Memory API (FFM) introduced in
 * JDK 22+ to provide direct access to native USB libraries without JNI.
 * 
 * <p>This implementation is designed to work reliably on modern platforms, particularly macOS
 * Sequoia, where libusb (used by usb4java) has known compatibility issues. It requires JDK 23 or
 * higher for stable FFM API support.
 * 
 * <p>Usage example:
 * <pre>
 * // Initialize the JavaDoesUSB connection interface
 * JocdConnJavaDoesUsb.init();
 * 
 * // Now use jOCD as normal
 * HashMap&lt;String, String&gt; boards = Jocd.getAllConnectedBoardsName();
 * </pre>
 * 
 * @see br.org.certi.jocd.Jocd
 * @see br.org.certi.jocdconnjavadoesusb.connectioninterface.JavaDoesUsbDevice
 */
public class JocdConnJavaDoesUsb {

  /**
   * Initializes the Connection Interface in jOCD with JavaDoesUSB backend. This method must be
   * executed before any other jOCD library call.
   * 
   * <p>This method creates a new JavaDoesUsbDevice instance and registers it as the connection
   * interface for the jOCD core library. All subsequent USB operations will use JavaDoesUSB for
   * device communication.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>1.1: Registers JavaDoesUSB-based ConnectionInterface implementation</li>
   *   <li>2.2: Exposes static init() method as public API</li>
   *   <li>2.5: Assigns JavaDoesUSB device implementation to Jocd.connectionInterface</li>
   * </ul>
   */
  public static void init() {
    Jocd.connectionInterface = new JavaDoesUsbDevice();
  }
}
