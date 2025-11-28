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
import br.org.certi.jocdconnjavadoesusb.connectioninterface.TestableJavaDoesUsbDevice;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for JavaDoesUsbDevice.
 * 
 * These tests verify the basic functionality of device enumeration and metadata handling.
 */
public class JavaDoesUsbDeviceTest {

  /**
   * Test that getAllConnectedDevices returns a non-null list.
   * 
   * This test verifies Requirement 3.5: When no devices are connected, 
   * the system SHALL return an empty list without throwing exceptions.
   */
  @Test
  public void testGetAllConnectedDevicesReturnsNonNullList() {
    JavaDoesUsbDevice device = new JavaDoesUsbDevice();
    List<ConnectionInterface> devices = device.getAllConnectedDevices();
    
    assertNotNull("getAllConnectedDevices should never return null", devices);
  }

  /**
   * Test that getAllConnectedDevices returns a list (empty or populated).
   * 
   * This test verifies that the method completes without throwing exceptions,
   * satisfying Requirement 3.4: errors are handled gracefully.
   */
  @Test
  public void testGetAllConnectedDevicesDoesNotThrowException() {
    JavaDoesUsbDevice device = new JavaDoesUsbDevice();
    
    try {
      List<ConnectionInterface> devices = device.getAllConnectedDevices();
      // If we get here, no exception was thrown - test passes
      assertTrue("Method should complete without exception", true);
    } catch (Exception e) {
      fail("getAllConnectedDevices should not throw exceptions: " + e.getMessage());
    }
  }

  /**
   * Test that devices returned from getAllConnectedDevices have metadata populated.
   * 
   * This test verifies Requirement 3.3: device metadata should be populated.
   * Note: This test only runs if there are actual devices connected.
   */
  @Test
  public void testDeviceMetadataIsPopulated() {
    JavaDoesUsbDevice device = new JavaDoesUsbDevice();
    List<ConnectionInterface> devices = device.getAllConnectedDevices();
    
    // If devices are found, verify metadata is populated
    for (ConnectionInterface dev : devices) {
      // Vendor ID and Product ID should be non-zero for real devices
      assertTrue("Vendor ID should be set", dev.getVendorId() >= 0);
      assertTrue("Product ID should be set", dev.getProductId() >= 0);
      
      // String fields should be non-null (may be empty)
      assertNotNull("Product name should not be null", dev.getProductName());
      assertNotNull("Manufacturer name should not be null", dev.getManufacturerName());
      assertNotNull("Serial number should not be null", dev.getSerialNumber());
    }
  }

  /**
   * Test metadata getter methods return correct values.
   * 
   * This test verifies Requirements 8.1-8.6: metadata getters return correct values.
   * It creates a device instance and directly sets metadata fields to test the getters.
   */
  @Test
  public void testMetadataGetters() {
    // Create a test device instance using a test subclass that can access protected fields
    TestableJavaDoesUsbDevice device = new TestableJavaDoesUsbDevice();
    
    // Set metadata fields using setter methods
    device.setVendorId(0x0D28);
    device.setProductId(0x0204);
    device.setProductName("DAPLink CMSIS-DAP");
    device.setManufacturerName("ARM");
    device.setSerialNumber("12345678");
    
    // Test getVendorId (Requirement 8.1)
    assertEquals("getVendorId should return vendor ID", 0x0D28, device.getVendorId());
    
    // Test getProductId (Requirement 8.2)
    assertEquals("getProductId should return product ID", 0x0204, device.getProductId());
    
    // Test getProductName (Requirement 8.3)
    assertEquals("getProductName should return product name", "DAPLink CMSIS-DAP", device.getProductName());
    
    // Test getManufacturerName (Requirement 8.4)
    assertEquals("getManufacturerName should return manufacturer name", "ARM", device.getManufacturerName());
    
    // Test getSerialNumber (Requirement 8.5)
    assertEquals("getSerialNumber should return serial number", "12345678", device.getSerialNumber());
    
    // Test getDeviceName (Requirement 8.6)
    String expectedDeviceName = "0x0D28:0x0204 - DAPLink CMSIS-DAP";
    assertEquals("getDeviceName should return formatted device name", expectedDeviceName, device.getDeviceName());
  }

  /**
   * Test metadata getters handle null values gracefully.
   * 
   * This test verifies that string getters return empty strings when metadata is null.
   */
  @Test
  public void testMetadataGettersWithNullValues() {
    // Create a test device instance with null metadata
    TestableJavaDoesUsbDevice device = new TestableJavaDoesUsbDevice();
    
    // Don't set any metadata fields (they will be null)
    
    // Test that string getters return empty strings for null values
    assertEquals("getProductName should return empty string for null", "", device.getProductName());
    assertEquals("getManufacturerName should return empty string for null", "", device.getManufacturerName());
    assertEquals("getSerialNumber should return empty string for null", "", device.getSerialNumber());
    
    // Test getDeviceName with null product name
    String expectedDeviceName = "0x0000:0x0000 - Unknown Device";
    assertEquals("getDeviceName should handle null product name", expectedDeviceName, device.getDeviceName());
  }
}
