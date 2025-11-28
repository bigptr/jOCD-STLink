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
package br.org.certi.jocdconnjavadoesusb.stlink;

import br.org.certi.jocd.dapaccess.dapexceptions.Error;
import br.org.certi.jocd.stlink.StLinkInterface;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.codecrete.usb.Usb;
import net.codecrete.usb.UsbDevice;
import net.codecrete.usb.UsbDirection;
import net.codecrete.usb.UsbEndpoint;
import net.codecrete.usb.UsbInterface;

/**
 * JavaDoesUSB implementation of ST-Link interface.
 */
public class JavaDoesUsbStLink implements StLinkInterface {

  private static final Logger LOGGER = Logger.getLogger(JavaDoesUsbStLink.class.getName());

  // STMicroelectronics USB Vendor ID
  private static final int STM_VENDOR_ID = 0x0483;
  
  // ST-Link V2 Product IDs
  private static final int STLINK_V2_PID = 0x3748;
  private static final int STLINK_V2_1_PID = 0x374B;
  private static final int STLINK_V3_PID = 0x374F;
  private static final int STLINK_V3E_PID = 0x374E;
  
  private static final int TIMEOUT_MS = 5000;  // 5 second timeout for flash operations
  private static final int CMD_SIZE = 16;

  private UsbDevice device;
  private String serialNumber;
  private int inEndpoint;
  private int outEndpoint;
  private int interfaceNumber;

  /**
   * Create ST-Link interface for a specific device.
   */
  public JavaDoesUsbStLink(UsbDevice device) {
    this.device = device;
    this.serialNumber = device.getSerialNumber();
  }

  /**
   * Find all connected ST-Link devices.
   */
  public static List<UsbDevice> findDevices() {
    return Usb.findDevices(d -> 
        d.getVendorId() == STM_VENDOR_ID && 
        (d.getProductId() == STLINK_V2_PID || 
         d.getProductId() == STLINK_V2_1_PID ||
         d.getProductId() == STLINK_V3_PID ||
         d.getProductId() == STLINK_V3E_PID));
  }

  /**
   * Get the first connected ST-Link device.
   */
  public static JavaDoesUsbStLink getFirstDevice() {
    List<UsbDevice> devices = findDevices();
    if (devices.isEmpty()) {
      return null;
    }
    return new JavaDoesUsbStLink(devices.get(0));
  }

  @Override
  public void open() throws Error {
    try {
      device.open();
      
      // Find the debug interface (interface 0, vendor-specific class 0xFF)
      List<UsbInterface> interfaces = device.getInterfaces();
      
      for (UsbInterface iface : interfaces) {
        if (!iface.getAlternates().isEmpty()) {
          var alt = iface.getAlternates().get(0);
          
          // Look for vendor-specific interface (debug interface)
          if (alt.getClassCode() == 0xFF) {
            interfaceNumber = iface.getNumber();
            
            // Find endpoints
            for (UsbEndpoint ep : alt.getEndpoints()) {
              if (ep.getDirection() == UsbDirection.IN && inEndpoint == 0) {
                inEndpoint = ep.getNumber();
              } else if (ep.getDirection() == UsbDirection.OUT && outEndpoint == 0) {
                outEndpoint = ep.getNumber();
              }
            }
            
            if (inEndpoint != 0 && outEndpoint != 0) {
              break;
            }
          }
        }
      }
      
      if (inEndpoint == 0 || outEndpoint == 0) {
        throw new Error("Could not find ST-Link debug endpoints");
      }
      
      device.claimInterface(interfaceNumber);
      
      LOGGER.log(Level.INFO, String.format(
          "Opened ST-Link: interface=%d, IN=%d, OUT=%d", 
          interfaceNumber, inEndpoint, outEndpoint));
      
    } catch (Exception e) {
      throw new Error("Failed to open ST-Link: " + e.getMessage());
    }
  }

  @Override
  public void close() {
    try {
      if (device != null) {
        try {
          device.releaseInterface(interfaceNumber);
        } catch (Exception e) {
          // Ignore - device may not be open
        }
        try {
          device.close();
        } catch (Exception e) {
          // Ignore - device may not be open
        }
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error closing ST-Link: " + e.getMessage());
    }
  }

  @Override
  public byte[] transfer(byte[] command, int responseLength) throws Error, TimeoutException {
    try {
      // Pad command to CMD_SIZE (16 bytes per ST-Link protocol)
      byte[] cmd = new byte[CMD_SIZE];
      System.arraycopy(command, 0, cmd, 0, Math.min(command.length, CMD_SIZE));
      
      // Send command
      device.transferOut(outEndpoint, cmd, TIMEOUT_MS);
      
      // Read response if expected
      if (responseLength > 0) {
        // Small delay between transfers for macOS IOKit stability
        Thread.sleep(2);
        
        // Read at least max packet size (64 bytes) as per pyOCD implementation
        int readSize = Math.max(responseLength, 64);
        byte[] response = device.transferIn(inEndpoint, readSize);
        
        // Return only the requested number of bytes
        if (response.length > responseLength) {
          byte[] trimmed = new byte[responseLength];
          System.arraycopy(response, 0, trimmed, 0, responseLength);
          return trimmed;
        }
        return response;
      }
      
      return new byte[0];
      
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout")) {
        throw new TimeoutException("ST-Link transfer timed out");
      }
      throw new Error("ST-Link transfer failed: " + e.getMessage());
    }
  }

  @Override
  public void transferOut(byte[] command, byte[] data) throws Error, TimeoutException {
    try {
      // Pad command to CMD_SIZE
      byte[] cmd = new byte[CMD_SIZE];
      System.arraycopy(command, 0, cmd, 0, Math.min(command.length, CMD_SIZE));
      
      // Send command
      device.transferOut(outEndpoint, cmd, TIMEOUT_MS);
      
      // Small delay between transfers for macOS IOKit stability
      Thread.sleep(2);
      
      // Send data
      device.transferOut(outEndpoint, data, TIMEOUT_MS);
      
      // Small delay after transfer for macOS IOKit stability
      Thread.sleep(2);
      
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout")) {
        throw new TimeoutException("ST-Link transfer timed out");
      }
      throw new Error("ST-Link transfer failed: " + e.getMessage());
    }
  }

  @Override
  public byte[] transferIn(byte[] command, int length) throws Error, TimeoutException {
    try {
      // Pad command to CMD_SIZE (16 bytes per ST-Link protocol)
      byte[] cmd = new byte[CMD_SIZE];
      System.arraycopy(command, 0, cmd, 0, Math.min(command.length, CMD_SIZE));
      
      // Send command
      device.transferOut(outEndpoint, cmd, TIMEOUT_MS);
      
      // Small delay between transfers for macOS IOKit stability
      Thread.sleep(1);
      
      // Read data in 64-byte chunks (USB packet size)
      byte[] result = new byte[length];
      int offset = 0;
      
      while (offset < length) {
        int remaining = length - offset;
        // Always read in multiples of 64 bytes (packet size)
        int readSize = Math.min(remaining, 64);
        if (readSize < 64 && remaining > 0) {
          // For the last partial read, still request 64 bytes
          readSize = 64;
        }
        
        byte[] chunk = device.transferIn(inEndpoint, readSize);
        
        // Copy only what we need
        int toCopy = Math.min(chunk.length, length - offset);
        System.arraycopy(chunk, 0, result, offset, toCopy);
        offset += toCopy;
        
        // If we got less than expected, we're done
        if (chunk.length < 64) {
          break;
        }
      }
      
      return result;
      
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout")) {
        throw new TimeoutException("ST-Link transfer timed out");
      }
      throw new Error("ST-Link transfer failed: " + e.getMessage());
    }
  }

  @Override
  public String getSerialNumber() {
    return serialNumber;
  }
  
  /**
   * Get the underlying USB device.
   */
  public UsbDevice getDevice() {
    return device;
  }
}
