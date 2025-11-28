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

import br.org.certi.jocd.dapaccess.connectioninterface.ConnectionInterface;
import br.org.certi.jocd.dapaccess.dapexceptions.Error;
import br.org.certi.jocd.dapaccess.dapexceptions.InsufficientPermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.codecrete.usb.Usb;
import net.codecrete.usb.UsbControlTransfer;
import net.codecrete.usb.UsbDevice;
import net.codecrete.usb.UsbDirection;
import net.codecrete.usb.UsbEndpoint;
import net.codecrete.usb.UsbInterface;
import net.codecrete.usb.UsbRecipient;
import net.codecrete.usb.UsbRequestType;

/**
 * JavaDoesUsbDevice implements the ConnectionInterface using the JavaDoesUSB library.
 * 
 * <p>This implementation uses JavaDoesUSB's Foreign Function & Memory (FFM) API to communicate
 * with USB devices. It is designed to work reliably on modern platforms, particularly macOS
 * Sequoia, where libusb has known compatibility issues.
 * 
 * <p>The class manages USB device connections for CMSIS-DAP protocol communication, handling
 * device enumeration, opening/closing connections, and reading/writing data via HID endpoints.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>2.1: Package structure br.org.certi.jocdconnjavadoesusb.connectioninterface</li>
 *   <li>2.3: ConnectionInterface implementation in connectioninterface subpackage</li>
 *   <li>9.5: Logger initialization with class-specific logger name</li>
 * </ul>
 */
public class JavaDoesUsbDevice implements ConnectionInterface {

  // Logging
  private static final String CLASS_NAME = JavaDoesUsbDevice.class.getName();
  private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

  // USB HID interface class code
  private static final int USB_CLASS_HID = 0x03;
  
  // USB Vendor-specific interface class code (used by ST-Link)
  private static final int USB_CLASS_VENDOR_SPECIFIC = 0xFF;
  
  // STMicroelectronics USB Vendor ID
  private static final int STM_VENDOR_ID = 0x0483;

  // Default timeout for read operations in milliseconds
  private static final int DEFAULT_READ_TIMEOUT_MS = 200;

  // Default packet size for HID devices (typically 64 bytes)
  private static final int DEFAULT_PACKET_SIZE = 64;

  // Device metadata fields
  protected int vendorId;
  protected int productId;
  protected String productName;
  protected String manufacturerName;
  protected String serialNumber;

  // JavaDoesUSB device handle
  protected UsbDevice device;

  // Atomic flag to prevent duplicate open operations
  private AtomicBoolean atomicOpen = new AtomicBoolean(false);

  // Packet configuration
  private int packetCount = 1;
  private int packetSize = DEFAULT_PACKET_SIZE;

  // USB interface and endpoint references
  private UsbInterface usbInterface;
  private int interfaceNumber = -1;
  private UsbEndpoint inputEndpoint;
  private UsbEndpoint outputEndpoint;

  /**
   * Default constructor for JavaDoesUsbDevice.
   * 
   * <p>Initializes a new instance with default values. The device will not be connected to any
   * physical USB device until getAllConnectedDevices() is called and a specific device is selected.
   */
  public JavaDoesUsbDevice() {
    // Initialize with default values
    // Device-specific fields will be populated during enumeration
  }

  /**
   * Returns all connected CMSIS-DAP devices.
   * 
   * <p>This method enumerates all USB devices accessible via JavaDoesUSB and filters for devices
   * with HID interface class 0x03, which is used by CMSIS-DAP protocol. For each matching device,
   * it populates metadata including vendor ID, product ID, product name, manufacturer name, and
   * serial number.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>3.1: Returns list of all USB devices accessible via JavaDoesUSB</li>
   *   <li>3.2: Filters for devices with HID interface class 0x03</li>
   *   <li>3.3: Populates vendor ID, product ID, product name, manufacturer name, serial number</li>
   *   <li>3.4: Logs errors and continues enumerating remaining devices</li>
   *   <li>3.5: Returns empty list when no devices are connected</li>
   *   <li>9.2: Logs each discovered device with its metadata</li>
   * </ul>
   * 
   * @return List of ConnectionInterface instances representing connected CMSIS-DAP devices.
   *         Returns empty list if no devices are found or if enumeration fails.
   */
  @Override
  public List<ConnectionInterface> getAllConnectedDevices() {
    List<ConnectionInterface> deviceList = new ArrayList<>();
    
    LOGGER.log(Level.FINE, "Starting device enumeration...");
    
    try {
      // Get all connected USB devices using JavaDoesUSB API
      List<UsbDevice> usbDevices = Usb.findDevices(device -> true);
      
      LOGGER.log(Level.FINE, "Found " + usbDevices.size() + " USB devices");
      
      // Iterate through each device and check for compatible interface
      for (UsbDevice usbDevice : usbDevices) {
        try {
          // Check if this device has a compatible interface:
          // - HID interface (class 0x03) for CMSIS-DAP devices
          // - Vendor-specific interface (class 0xFF) for ST-Link devices
          boolean hasCompatibleInterface = false;
          
          for (UsbInterface iface : usbDevice.getInterfaces()) {
            // Check interface class code via alternate descriptor
            // UsbInterface.getAlternate(0) returns the first alternate setting
            // which contains the interface class code
            if (!iface.getAlternates().isEmpty()) {
              int classCode = iface.getAlternates().get(0).getClassCode();
              
              // Accept HID class (CMSIS-DAP)
              if (classCode == USB_CLASS_HID) {
                hasCompatibleInterface = true;
                break;
              }
              
              // Accept vendor-specific class from STMicroelectronics (ST-Link)
              if (classCode == USB_CLASS_VENDOR_SPECIFIC && 
                  usbDevice.getVendorId() == STM_VENDOR_ID) {
                hasCompatibleInterface = true;
                break;
              }
            }
          }
          
          // Skip devices without compatible interface
          if (!hasCompatibleInterface) {
            continue;
          }
          
          // Create a new device instance for this USB device
          JavaDoesUsbDevice deviceInstance = new JavaDoesUsbDevice();
          deviceInstance.device = usbDevice;
          
          // Populate device metadata
          deviceInstance.vendorId = usbDevice.getVendorId();
          deviceInstance.productId = usbDevice.getProductId();
          
          // Get string descriptors with error handling
          try {
            deviceInstance.productName = usbDevice.getProduct();
            if (deviceInstance.productName == null) {
              deviceInstance.productName = "";
            }
          } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not retrieve product name for device " + 
                String.format("0x%04X:0x%04X", deviceInstance.vendorId, deviceInstance.productId) + 
                ": " + e.getMessage());
            deviceInstance.productName = "";
          }
          
          try {
            deviceInstance.manufacturerName = usbDevice.getManufacturer();
            if (deviceInstance.manufacturerName == null) {
              deviceInstance.manufacturerName = "";
            }
          } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not retrieve manufacturer name for device " + 
                String.format("0x%04X:0x%04X", deviceInstance.vendorId, deviceInstance.productId) + 
                ": " + e.getMessage());
            deviceInstance.manufacturerName = "";
          }
          
          try {
            deviceInstance.serialNumber = usbDevice.getSerialNumber();
            if (deviceInstance.serialNumber == null) {
              deviceInstance.serialNumber = "";
            }
          } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not retrieve serial number for device " + 
                String.format("0x%04X:0x%04X", deviceInstance.vendorId, deviceInstance.productId) + 
                ": " + e.getMessage());
            deviceInstance.serialNumber = "";
          }
          
          // Add device to the list
          deviceList.add(deviceInstance);
          
          // Log discovered device details
          LOGGER.log(Level.FINE, "Discovered CMSIS-DAP device:\n" +
              "  Vendor ID: 0x" + String.format("%04X", deviceInstance.vendorId) + "\n" +
              "  Product ID: 0x" + String.format("%04X", deviceInstance.productId) + "\n" +
              "  Product Name: " + deviceInstance.productName + "\n" +
              "  Manufacturer Name: " + deviceInstance.manufacturerName + "\n" +
              "  Serial Number: " + deviceInstance.serialNumber);
          
        } catch (Exception e) {
          // Log error and continue with remaining devices (Requirement 3.4)
          LOGGER.log(Level.SEVERE, "Error processing USB device during enumeration: " + 
              e.getMessage(), e);
        }
      }
      
      LOGGER.log(Level.INFO, "Device enumeration complete. Found " + deviceList.size() + 
          " CMSIS-DAP device(s)");
      
    } catch (Exception e) {
      // Handle top-level enumeration errors gracefully
      LOGGER.log(Level.SEVERE, "Failed to enumerate USB devices: " + e.getMessage(), e);
      return new ArrayList<>();
    }
    
    return deviceList;
  }

  /**
   * Returns the USB vendor ID of the device.
   * 
   * <p>The vendor ID is a 16-bit identifier assigned by the USB Implementers Forum (USB-IF)
   * that uniquely identifies the device manufacturer.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.1: Returns USB vendor ID as an integer</li>
   * </ul>
   * 
   * @return The USB vendor ID as an integer
   */
  @Override
  public int getVendorId() {
    return vendorId;
  }

  /**
   * Returns the USB product ID of the device.
   * 
   * <p>The product ID is a 16-bit identifier assigned by the device manufacturer
   * that identifies the specific product model.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.2: Returns USB product ID as an integer</li>
   * </ul>
   * 
   * @return The USB product ID as an integer
   */
  @Override
  public int getProductId() {
    return productId;
  }

  /**
   * Returns a string representation of the device.
   * 
   * <p>This method constructs a human-readable device identifier string that includes
   * the vendor ID, product ID, and product name. The format is:
   * "VID:PID - Product Name" (e.g., "0x0D28:0x0204 - DAPLink CMSIS-DAP")
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.6: Returns string representation of the device</li>
   * </ul>
   * 
   * @return A string representation of the device
   */
  @Override
  public String getDeviceName() {
    return String.format("0x%04X:0x%04X - %s", vendorId, productId, 
        productName != null ? productName : "Unknown Device");
  }

  /**
   * Returns the product name string descriptor.
   * 
   * <p>The product name is retrieved from the device's USB string descriptor during
   * device enumeration. If the descriptor is not available or cannot be read, an
   * empty string is returned.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.3: Returns product string descriptor</li>
   * </ul>
   * 
   * @return The product name string, or empty string if not available
   */
  @Override
  public String getProductName() {
    return productName != null ? productName : "";
  }

  /**
   * Returns the manufacturer name string descriptor.
   * 
   * <p>The manufacturer name is retrieved from the device's USB string descriptor during
   * device enumeration. If the descriptor is not available or cannot be read, an
   * empty string is returned.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.4: Returns manufacturer string descriptor</li>
   * </ul>
   * 
   * @return The manufacturer name string, or empty string if not available
   */
  @Override
  public String getManufacturerName() {
    return manufacturerName != null ? manufacturerName : "";
  }

  /**
   * Returns the serial number string descriptor.
   * 
   * <p>The serial number is retrieved from the device's USB string descriptor during
   * device enumeration. If the descriptor is not available or cannot be read, an
   * empty string is returned. The serial number uniquely identifies this specific
   * device instance.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>8.5: Returns serial number string descriptor</li>
   * </ul>
   * 
   * @return The serial number string, or empty string if not available
   */
  @Override
  public String getSerialNumber() {
    return serialNumber != null ? serialNumber : "";
  }

  /**
   * Reads data from the USB device with default timeout.
   * 
   * <p>This method reads a packet of data from the device using the default timeout of 200ms.
   * It delegates to the overloaded read(int timeout) method.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>6.1: Performs bulk transfer on IN endpoint with default 200ms timeout</li>
   * </ul>
   * 
   * @return Byte array containing the packet data received from the device
   * @throws TimeoutException if no data is received within the timeout period
   */
  @Override
  public byte[] read() throws TimeoutException {
    return read(DEFAULT_READ_TIMEOUT_MS);
  }

  /**
   * Reads data from the USB device with specified timeout.
   * 
   * <p>This method performs a bulk transfer on the IN endpoint to read data from the device.
   * If zero bytes are received, it retries until the timeout expires. This handles cases where
   * the device may not have data ready immediately.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>6.1: Performs bulk transfer on IN endpoint with specified timeout</li>
   *   <li>6.2: Returns byte array containing packet data</li>
   *   <li>6.3: Throws TimeoutException on timeout</li>
   *   <li>6.4: Retries on zero bytes received until timeout</li>
   *   <li>6.5: Handles null device case</li>
   *   <li>9.1: Logs read operations</li>
   *   <li>9.4: Logs timeout events</li>
   * </ul>
   * 
   * @param timeout Timeout in milliseconds for the read operation
   * @return Byte array containing the packet data received from the device
   * @throws TimeoutException if no data is received within the timeout period
   */
  public byte[] read(int timeout) throws TimeoutException {
    // Handle null device case (Requirement 6.5)
    if (device == null) {
      LOGGER.log(Level.SEVERE, "Cannot read: device is null");
      return null;
    }
    
    // Validate that we have an IN endpoint
    if (inputEndpoint == null) {
      LOGGER.log(Level.SEVERE, "Cannot read: IN endpoint is null");
      return null;
    }
    
    LOGGER.log(Level.FINE, "Reading from device with timeout " + timeout + "ms");
    
    // Calculate absolute timeout deadline
    long startTime = System.currentTimeMillis();
    long deadline = startTime + timeout;
    
    // Retry loop for zero-byte reads (Requirement 6.4)
    while (true) {
      try {
        // Calculate remaining timeout for this attempt
        long remainingTimeout = deadline - System.currentTimeMillis();
        
        if (remainingTimeout <= 0) {
          // Timeout expired (Requirement 6.3)
          String timeoutMsg = "Read operation timed out after " + timeout + "ms";
          LOGGER.log(Level.WARNING, timeoutMsg);
          throw new TimeoutException(timeoutMsg);
        }
        
        // Perform bulk transfer on IN endpoint (Requirement 6.1)
        // JavaDoesUSB transferIn returns the data directly
        byte[] data = device.transferIn(inputEndpoint.getNumber(), packetSize);
        
        // Check if we received data
        if (data != null && data.length > 0) {
          // Success - return the data (Requirement 6.2)
          LOGGER.log(Level.FINE, "Successfully read " + data.length + " bytes from device");
          return data;
        }
        
        // Zero bytes received - retry (Requirement 6.4)
        LOGGER.log(Level.FINE, "Received 0 bytes, retrying...");
        
        // Small delay before retry to avoid busy-waiting
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new TimeoutException("Read operation interrupted");
        }
        
      } catch (TimeoutException e) {
        // Re-throw TimeoutException
        throw e;
      } catch (Exception e) {
        // Handle other USB transfer errors
        String errorMsg = "Read operation failed: " + e.getMessage();
        LOGGER.log(Level.SEVERE, errorMsg, e);
        
        // Check if this is a timeout-related error
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
          throw new TimeoutException("Read operation timed out: " + e.getMessage());
        }
        
        // For other errors, return null
        return null;
      }
    }
  }

  /**
   * Writes data to the USB device.
   * 
   * <p>This method sends CMSIS-DAP command data to the device. The data is padded to the
   * configured packet size with zero bytes. The transmission method depends on endpoint
   * availability:
   * <ul>
   *   <li>If OUT endpoint exists: Uses bulk transfer</li>
   *   <li>If no OUT endpoint: Uses control transfer with HID Set_REPORT request</li>
   * </ul>
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>5.1: Pads data to endpoint packet size with zero bytes</li>
   *   <li>5.2: Uses bulk transfer when OUT endpoint exists</li>
   *   <li>5.3: Uses control transfer when no OUT endpoint (Set_REPORT request)</li>
   *   <li>5.4: Sets correct control transfer parameters (requestType=0x21, request=0x09, value=0x200)</li>
   *   <li>5.5: Throws Error exception on write failures</li>
   *   <li>5.6: Handles null device/interface cases</li>
   *   <li>9.1: Logs write operations</li>
   * </ul>
   * 
   * @param data The command data to write to the device
   * @throws Error if the write operation fails or device is not open
   */
  @Override
  public void write(byte[] data) throws Error {
    // Validate device state (Requirement 5.6)
    if (device == null) {
      LOGGER.log(Level.SEVERE, "Cannot write: device is null");
      throw new Error("Device is not open");
    }
    
    if (usbInterface == null) {
      LOGGER.log(Level.SEVERE, "Cannot write: USB interface is null");
      throw new Error("Device is not open or interface not claimed");
    }
    
    // Pad data to packet size with zeros (Requirement 5.1)
    byte[] paddedData = new byte[packetSize];
    int copyLength = Math.min(data.length, packetSize);
    System.arraycopy(data, 0, paddedData, 0, copyLength);
    // Remaining bytes are already zero-initialized
    
    LOGGER.log(Level.FINE, "Writing " + data.length + " bytes (padded to " + 
        packetSize + " bytes) to device");
    
    try {
      if (outputEndpoint != null) {
        // Use bulk transfer when OUT endpoint exists (Requirement 5.2)
        LOGGER.log(Level.FINE, "Using bulk transfer to OUT endpoint");
        
        device.transferOut(outputEndpoint.getNumber(), paddedData);
        
        LOGGER.log(Level.FINE, "Bulk transfer completed successfully");
        
      } else {
        // Use control transfer when no OUT endpoint (Requirement 5.3)
        LOGGER.log(Level.FINE, "Using control transfer (no OUT endpoint available)");
        
        // HID Set_REPORT request parameters (Requirement 5.4)
        // requestType: 0x21 = Host-to-Device, Class, Interface
        // request: 0x09 = HID Set_REPORT
        // value: 0x200 = Report Type (Output) | Report ID (0)
        // index: interface number
        UsbControlTransfer controlTransfer = new UsbControlTransfer(
            UsbRequestType.CLASS,
            UsbRecipient.INTERFACE,
            0x09,  // HID Set_REPORT request
            0x200, // Report Type (Output) | Report ID (0)
            interfaceNumber
        );
        
        device.controlTransferOut(controlTransfer, paddedData);
        
        LOGGER.log(Level.FINE, "Control transfer completed successfully");
      }
      
    } catch (Exception e) {
      // Throw Error exception on write failures (Requirement 5.5)
      String errorMsg = "Write operation failed: " + e.getMessage();
      LOGGER.log(Level.SEVERE, errorMsg, e);
      throw new Error(errorMsg);
    }
  }

  /**
   * Opens the USB device connection.
   * 
   * <p>This method performs the following operations:
   * <ol>
   *   <li>Checks atomic state to prevent duplicate open operations</li>
   *   <li>Locates the HID interface (class 0x03)</li>
   *   <li>Claims the USB interface for exclusive access</li>
   *   <li>Identifies and configures IN/OUT endpoints</li>
   *   <li>Reads packet size from endpoint descriptors</li>
   * </ol>
   * 
   * <p>If any step fails, the method cleans up partial state and logs the error.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>4.1: Locates HID interface with class code 0x03</li>
   *   <li>4.2: Claims interface for exclusive access</li>
   *   <li>4.3: Identifies and configures IN endpoint</li>
   *   <li>4.4: Identifies and configures OUT endpoint if present</li>
   *   <li>4.5: Prevents duplicate open operations using atomic state</li>
   *   <li>4.6: Throws InsufficientPermissions on permission errors</li>
   *   <li>4.7: Cleans up partial state on failure</li>
   *   <li>9.1: Logs operations at appropriate levels</li>
   * </ul>
   * 
   * @throws InsufficientPermissions if the interface cannot be claimed due to permission issues
   */
  @Override
  public void open() throws InsufficientPermissions {
    // Prevent duplicate open operations (Requirement 4.5)
    if (!atomicOpen.compareAndSet(false, true)) {
      LOGGER.log(Level.WARNING, "Attempting to open USB device that is already open. Ignoring.");
      return;
    }

    // Use do-while(false) pattern for easy cleanup on failure
    do {
      // Validate device handle
      if (this.device == null) {
        LOGGER.log(Level.SEVERE, "Cannot open device: device handle is null");
        break;
      }

      LOGGER.log(Level.INFO, "Opening USB device: " + 
          String.format("0x%04X:0x%04X", vendorId, productId));

      // Open the USB device first (required before claiming interface)
      try {
        device.open();
        LOGGER.log(Level.FINE, "Successfully opened USB device");
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Failed to open USB device: " + e.getMessage(), e);
        break;
      }

      // Look for debug interface (Requirement 4.1)
      if (!lookForHidInterface()) {
        LOGGER.log(Level.SEVERE, "Failed to find debug interface on device");
        break;
      }

      LOGGER.log(Level.FINE, "Found debug interface at index " + interfaceNumber);

      // Claim the USB interface (Requirement 4.2)
      try {
        device.claimInterface(interfaceNumber);
        LOGGER.log(Level.FINE, "Successfully claimed USB interface " + interfaceNumber);
      } catch (Exception e) {
        // Check if this is a permission issue
        String errorMsg = e.getMessage();
        if (errorMsg != null && (errorMsg.contains("permission") || 
                                  errorMsg.contains("access denied") ||
                                  errorMsg.contains("Access is denied"))) {
          throw new InsufficientPermissions("Cannot claim USB interface due to insufficient permissions: " + e.getMessage());
        }
        LOGGER.log(Level.SEVERE, "Failed to claim USB interface: " + e.getMessage(), e);
        break;
      }

      // Find and configure endpoints (Requirements 4.3, 4.4)
      if (!findEndpoints()) {
        LOGGER.log(Level.SEVERE, "Failed to find required endpoints on HID interface");
        break;
      }

      LOGGER.log(Level.INFO, "Successfully opened USB device");
      
      // Success - return without cleanup
      return;

    } while (false);

    // If we reach here, something failed - clean up partial state (Requirement 4.7)
    LOGGER.log(Level.WARNING, "Open operation failed, cleaning up partial state");
    close();
  }

  /**
   * Locates the debug interface on the USB device.
   * 
   * <p>This helper method searches through all interfaces on the device to find one with:
   * <ul>
   *   <li>HID class code (0x03) for CMSIS-DAP devices</li>
   *   <li>Vendor-specific class code (0xFF) for ST-Link devices</li>
   * </ul>
   * When found, it stores the interface reference and interface number.
   * 
   * @return true if compatible interface was found, false otherwise
   */
  private boolean lookForHidInterface() {
    try {
      List<UsbInterface> interfaces = device.getInterfaces();
      
      for (int i = 0; i < interfaces.size(); i++) {
        UsbInterface iface = interfaces.get(i);
        
        // Check if this interface has a compatible class code
        // Get the first alternate setting to check the class code
        if (!iface.getAlternates().isEmpty()) {
          int classCode = iface.getAlternates().get(0).getClassCode();
          
          // Accept HID class (CMSIS-DAP)
          if (classCode == USB_CLASS_HID) {
            this.usbInterface = iface;
            this.interfaceNumber = iface.getNumber();
            return true;
          }
          
          // Accept vendor-specific class from STMicroelectronics (ST-Link)
          // Interface 0 is the debug interface on ST-Link devices
          if (classCode == USB_CLASS_VENDOR_SPECIFIC && 
              vendorId == STM_VENDOR_ID && 
              iface.getNumber() == 0) {
            this.usbInterface = iface;
            this.interfaceNumber = iface.getNumber();
            return true;
          }
        }
      }
      
      // No compatible interface found
      return false;
      
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Error while searching for debug interface: " + e.getMessage(), e);
      return false;
    }
  }

  /**
   * Identifies and configures IN and OUT endpoints on the HID interface.
   * 
   * <p>This helper method examines all endpoints on the HID interface to identify:
   * <ul>
   *   <li>IN endpoint (required): Used for reading data from device</li>
   *   <li>OUT endpoint (optional): Used for writing data to device</li>
   * </ul>
   * 
   * <p>If no OUT endpoint is found, write operations will use control transfers on endpoint 0.
   * The method also reads the maximum packet size from the endpoint descriptors.
   * 
   * @return true if required IN endpoint was found, false otherwise
   */
  private boolean findEndpoints() {
    try {
      // Get endpoints from the first alternate setting
      if (usbInterface.getAlternates().isEmpty()) {
        LOGGER.log(Level.SEVERE, "No alternate settings found on HID interface");
        return false;
      }
      
      List<UsbEndpoint> endpoints = usbInterface.getAlternates().get(0).getEndpoints();
      
      LOGGER.log(Level.FINE, "Found " + endpoints.size() + " endpoint(s) on HID interface");
      
      // Validate endpoint count (typically 1 or 2 for HID devices)
      if (endpoints.size() > 2) {
        LOGGER.log(Level.WARNING, "Found " + endpoints.size() + 
            " endpoints on HID interface, expected 1 or 2");
      }
      
      // Iterate through endpoints to identify IN and OUT
      // For ST-Link devices with multiple endpoints, prefer the first IN/OUT pair
      for (UsbEndpoint endpoint : endpoints) {
        int endpointNumber = endpoint.getNumber();
        UsbDirection direction = endpoint.getDirection();
        
        // Check direction using JavaDoesUSB API
        if (direction == UsbDirection.IN) {
          // Only use the first IN endpoint found (skip trace endpoints)
          if (this.inputEndpoint == null) {
            this.inputEndpoint = endpoint;
            
            // Read packet size from endpoint descriptor
            int maxPacketSize = endpoint.getPacketSize();
            if (maxPacketSize > 0) {
              this.packetSize = maxPacketSize;
            }
            
            LOGGER.log(Level.FINE, "Found IN endpoint: number=" + endpointNumber + 
                ", maxPacketSize=" + maxPacketSize);
          } else {
            LOGGER.log(Level.FINE, "Skipping additional IN endpoint: number=" + endpointNumber);
          }
        } else if (direction == UsbDirection.OUT) {
          // Only use the first OUT endpoint found
          if (this.outputEndpoint == null) {
            this.outputEndpoint = endpoint;
            
            LOGGER.log(Level.FINE, "Found OUT endpoint: number=" + endpointNumber);
          } else {
            LOGGER.log(Level.FINE, "Skipping additional OUT endpoint: number=" + endpointNumber);
          }
        }
      }
      
      // IN endpoint is required
      if (inputEndpoint == null) {
        LOGGER.log(Level.SEVERE, "No IN endpoint found on HID interface");
        return false;
      }
      
      // OUT endpoint is optional
      if (outputEndpoint == null) {
        LOGGER.log(Level.FINE, "No OUT endpoint found, will use control transfers for writes");
      }
      
      return true;
      
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Error while finding endpoints: " + e.getMessage(), e);
      return false;
    }
  }

  /**
   * Closes the USB device connection and releases all resources.
   * 
   * <p>This method performs cleanup operations in the following order:
   * <ol>
   *   <li>Releases the claimed USB interface</li>
   *   <li>Closes the device connection</li>
   *   <li>Resets endpoint references to null</li>
   *   <li>Resets interface number</li>
   *   <li>Resets atomic open state to allow reopening</li>
   * </ol>
   * 
   * <p>All exceptions during cleanup are suppressed to ensure complete cleanup even if
   * individual operations fail. This method is safe to call multiple times.
   * 
   * <p>Requirements satisfied:
   * <ul>
   *   <li>7.1: Releases the claimed USB interface</li>
   *   <li>7.2: Closes the device connection</li>
   *   <li>7.3: Resets endpoint references to null</li>
   *   <li>7.4: Resets atomic open state to allow reopening</li>
   *   <li>7.5: Suppresses exceptions during cleanup</li>
   *   <li>9.1: Logs close operations</li>
   * </ul>
   */
  @Override
  public void close() {
    LOGGER.log(Level.INFO, "Closing USB device");
    
    // Release USB interface (Requirement 7.1)
    if (usbInterface != null && device != null) {
      try {
        device.releaseInterface(interfaceNumber);
        LOGGER.log(Level.FINE, "Released USB interface " + interfaceNumber);
      } catch (Exception e) {
        // Suppress exceptions during cleanup (Requirement 7.5)
        LOGGER.log(Level.WARNING, "Error releasing USB interface during close: " + 
            e.getMessage(), e);
      }
    }
    
    // Close device connection (Requirement 7.2)
    if (device != null) {
      try {
        device.close();
        LOGGER.log(Level.FINE, "Closed device connection");
      } catch (Exception e) {
        // Suppress exceptions during cleanup (Requirement 7.5)
        LOGGER.log(Level.WARNING, "Error closing device connection: " + 
            e.getMessage(), e);
      }
    }
    
    // Reset endpoint references to null (Requirement 7.3)
    inputEndpoint = null;
    outputEndpoint = null;
    LOGGER.log(Level.FINE, "Reset endpoint references");
    
    // Reset interface number
    interfaceNumber = -1;
    
    // Reset USB interface reference
    usbInterface = null;
    
    // Reset atomic open state to allow reopening (Requirement 7.4)
    atomicOpen.set(false);
    LOGGER.log(Level.FINE, "Reset atomic open state");
    
    LOGGER.log(Level.INFO, "USB device closed successfully");
  }

  @Override
  public void setPacketCount(int packetCount) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void setPacketSize(int packetSize) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public int getPacketCount() {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
