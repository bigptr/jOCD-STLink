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
package br.org.certi.jocdconnjavadoesusb.connectioninterface;

/**
 * Test subclass that exposes protected fields for testing.
 * 
 * This class is in the same package as JavaDoesUsbDevice, allowing it to access
 * protected fields for unit testing purposes.
 */
public class TestableJavaDoesUsbDevice extends JavaDoesUsbDevice {
  
  /**
   * Sets the vendor ID for testing.
   */
  public void setVendorId(int vendorId) {
    this.vendorId = vendorId;
  }
  
  /**
   * Sets the product ID for testing.
   */
  public void setProductId(int productId) {
    this.productId = productId;
  }
  
  /**
   * Sets the product name for testing.
   */
  public void setProductName(String productName) {
    this.productName = productName;
  }
  
  /**
   * Sets the manufacturer name for testing.
   */
  public void setManufacturerName(String manufacturerName) {
    this.manufacturerName = manufacturerName;
  }
  
  /**
   * Sets the serial number for testing.
   */
  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }
}
