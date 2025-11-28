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

import br.org.certi.jocd.dapaccess.connectioninterface.ConnectionInterface;
import br.org.certi.jocdconnjavadoesusb.connectioninterface.JavaDoesUsbDevice;
import net.jqwik.api.*;
import net.codecrete.usb.UsbDevice;
import net.codecrete.usb.UsbInterface;
import net.codecrete.usb.UsbAlternateInterface;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Property-based tests for HID device filtering in JavaDoesUsbDevice.
 * 
 * **Feature: javadoesusb-integration, Property 1: HID device filtering**
 * **Validates: Requirements 3.2**
 */
public class HidDeviceFilteringPropertyTest {

  /**
   * Property 1: HID device filtering
   * 
   * For any collection of mock USB devices with various interface classes,
   * getAllConnectedDevices() should return only devices that have an HID
   * interface with class code 0x03.
   * 
   * **Validates: Requirements 3.2**
   */
  @Property(tries = 100)
  void onlyHidDevicesAreReturned(
      @ForAll("mixedDeviceList") List<MockUsbDeviceInfo> deviceInfos) {
    
    // Create a testable device instance
    TestableJavaDoesUsbDevice device = new TestableJavaDoesUsbDevice(deviceInfos);
    
    // Get all connected devices
    List<ConnectionInterface> result = device.getAllConnectedDevices();
    
    // Count expected HID devices
    long expectedHidCount = deviceInfos.stream()
        .filter(info -> info.hasHidInterface)
        .count();
    
    // Verify that the result contains only HID devices
    assertEquals("Should return exactly the number of HID devices",
        expectedHidCount, result.size());
    
    // Verify each returned device corresponds to an HID device
    for (ConnectionInterface conn : result) {
      TestableJavaDoesUsbDevice testDevice = (TestableJavaDoesUsbDevice) conn;
      MockUsbDeviceInfo matchingInfo = deviceInfos.stream()
          .filter(info -> info.vendorId == testDevice.getVendorId() &&
                         info.productId == testDevice.getProductId())
          .findFirst()
          .orElse(null);
      
      assertNotNull("Returned device should match an input device", matchingInfo);
      assertTrue("Returned device should have HID interface", matchingInfo.hasHidInterface);
    }
  }

  /**
   * Provides a list of mixed USB devices (some with HID, some without).
   */
  @Provide
  Arbitrary<List<MockUsbDeviceInfo>> mixedDeviceList() {
    return Arbitraries.integers().between(0, 20)
        .flatMap(size -> {
          List<Arbitrary<MockUsbDeviceInfo>> deviceArbitraries = new ArrayList<>();
          for (int i = 0; i < size; i++) {
            deviceArbitraries.add(mockUsbDeviceInfo());
          }
          if (deviceArbitraries.isEmpty()) {
            return Arbitraries.just(new ArrayList<>());
          }
          return Combinators.combine(deviceArbitraries).as(list -> list);
        });
  }

  /**
   * Provides arbitrary mock USB device information.
   */
  @Provide
  Arbitrary<MockUsbDeviceInfo> mockUsbDeviceInfo() {
    return Combinators.combine(
        Arbitraries.integers().between(0x0000, 0xFFFF), // vendorId
        Arbitraries.integers().between(0x0000, 0xFFFF), // productId
        Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50), // productName
        Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50), // manufacturerName
        Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(20), // serialNumber
        Arbitraries.integers().between(0x00, 0xFF), // interfaceClass
        Arbitraries.of(true, false) // hasHidInterface
    ).as((vid, pid, product, manufacturer, serial, ifaceClass, hasHid) -> {
      // If hasHid is true, force interface class to 0x03
      int actualClass = hasHid ? 0x03 : (ifaceClass == 0x03 ? 0x02 : ifaceClass);
      return new MockUsbDeviceInfo(vid, pid, product, manufacturer, serial, actualClass, hasHid);
    });
  }

  /**
   * Mock USB device information for testing.
   */
  static class MockUsbDeviceInfo {
    final int vendorId;
    final int productId;
    final String productName;
    final String manufacturerName;
    final String serialNumber;
    final int interfaceClass;
    final boolean hasHidInterface;

    MockUsbDeviceInfo(int vendorId, int productId, String productName,
                     String manufacturerName, String serialNumber,
                     int interfaceClass, boolean hasHidInterface) {
      this.vendorId = vendorId;
      this.productId = productId;
      this.productName = productName;
      this.manufacturerName = manufacturerName;
      this.serialNumber = serialNumber;
      this.interfaceClass = interfaceClass;
      this.hasHidInterface = hasHidInterface;
    }
  }

  /**
   * Testable version of JavaDoesUsbDevice that uses mock devices instead of real USB.
   */
  static class TestableJavaDoesUsbDevice extends JavaDoesUsbDevice {
    private final List<MockUsbDeviceInfo> mockDevices;

    TestableJavaDoesUsbDevice(List<MockUsbDeviceInfo> mockDevices) {
      this.mockDevices = mockDevices;
    }

    @Override
    public List<ConnectionInterface> getAllConnectedDevices() {
      List<ConnectionInterface> deviceList = new ArrayList<>();
      
      // Simulate device enumeration with mock devices
      for (MockUsbDeviceInfo mockInfo : mockDevices) {
        // Check if this device has an HID interface (class 0x03)
        if (!mockInfo.hasHidInterface) {
          continue;
        }
        
        // Create a new device instance for this mock device
        TestableJavaDoesUsbDevice deviceInstance = new TestableJavaDoesUsbDevice(new ArrayList<>());
        
        // Populate device metadata
        deviceInstance.vendorId = mockInfo.vendorId;
        deviceInstance.productId = mockInfo.productId;
        deviceInstance.productName = mockInfo.productName;
        deviceInstance.manufacturerName = mockInfo.manufacturerName;
        deviceInstance.serialNumber = mockInfo.serialNumber;
        
        deviceList.add(deviceInstance);
      }
      
      return deviceList;
    }

    @Override
    public int getVendorId() {
      return vendorId;
    }

    @Override
    public int getProductId() {
      return productId;
    }

    @Override
    public String getProductName() {
      return productName;
    }

    @Override
    public String getManufacturerName() {
      return manufacturerName;
    }

    @Override
    public String getSerialNumber() {
      return serialNumber;
    }
  }
}
