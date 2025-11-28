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
package br.org.certi.stlinkflashtool;

import br.org.certi.jocd.stlink.StLink;
import br.org.certi.jocd.stlink.StLinkFlasher;
import br.org.certi.jocd.tools.ProgressUpdateInterface;
import br.org.certi.jocdconnjavadoesusb.stlink.JavaDoesUsbStLink;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.List;
import net.codecrete.usb.UsbDevice;

/**
 * Simple command-line tool for flashing STM32 devices via ST-Link.
 * 
 * Usage:
 *   java StLinkFlashTool --list              List connected ST-Link devices
 *   java StLinkFlashTool firmware.bin        Flash binary file to 0x08000000
 *   java StLinkFlashTool firmware.bin 0x08000000  Flash to specific address
 */
public class StLinkFlashTool {

  private static final long DEFAULT_FLASH_ADDRESS = 0x08000000L;

  public static void main(String[] args) {
    System.out.println("\nST-Link Flash Tool for STM32");
    System.out.println("================================\n");

    if (args.length == 0) {
      printUsage();
      return;
    }

    try {
      if (args[0].equals("--list") || args[0].equals("-l")) {
        listDevices();
      } else if (args[0].equals("--help") || args[0].equals("-h")) {
        printUsage();
      } else {
        // Assume it's a file to flash
        String filename = args[0];
        long address = DEFAULT_FLASH_ADDRESS;
        
        if (args.length > 1) {
          address = parseAddress(args[1]);
        }
        
        flashFile(filename, address);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void printUsage() {
    System.out.println("Usage:");
    System.out.println("  StLinkFlashTool --list              List connected ST-Link devices");
    System.out.println("  StLinkFlashTool <file.bin>          Flash binary to 0x08000000");
    System.out.println("  StLinkFlashTool <file.bin> <addr>   Flash binary to specified address");
    System.out.println();
    System.out.println("Examples:");
    System.out.println("  StLinkFlashTool firmware.bin");
    System.out.println("  StLinkFlashTool firmware.bin 0x08000000");
  }

  private static void listDevices() {
    System.out.println("Searching for ST-Link devices...\n");
    
    List<UsbDevice> devices = JavaDoesUsbStLink.findDevices();
    
    if (devices.isEmpty()) {
      System.out.println("No ST-Link devices found.");
      return;
    }
    
    System.out.println("Found " + devices.size() + " ST-Link device(s):\n");
    
    for (int i = 0; i < devices.size(); i++) {
      UsbDevice device = devices.get(i);
      System.out.printf("  [%d] %s%n", i + 1, device.getProduct());
      System.out.printf("      Serial: %s%n", device.getSerialNumber());
      System.out.printf("      VID:PID = %04X:%04X%n", device.getVendorId(), device.getProductId());
      
      // Try to get version info
      try {
        JavaDoesUsbStLink iface = new JavaDoesUsbStLink(device);
        StLink stlink = new StLink(iface);
        stlink.open();
        System.out.printf("      ST-Link V%d, JTAG v%d%n", 
            stlink.getStLinkVersion(), stlink.getJtagVersion());
        
        long coreId = stlink.readCoreId();
        System.out.printf("      Core ID: 0x%08X%n", coreId);
        
        stlink.close();
      } catch (Exception e) {
        System.out.printf("      (Could not read version: %s)%n", e.getMessage());
      }
      
      System.out.println();
    }
  }

  private static void flashFile(String filename, long address) throws Exception {
    File file = new File(filename);
    
    if (!file.exists()) {
      throw new Exception("File not found: " + filename);
    }
    
    byte[] data = Files.readAllBytes(file.toPath());
    
    System.out.printf("File: %s (%d bytes)%n", filename, data.length);
    System.out.printf("Target address: 0x%08X%n", address);
    System.out.println();
    
    // Find ST-Link
    JavaDoesUsbStLink iface = JavaDoesUsbStLink.getFirstDevice();
    if (iface == null) {
      throw new Exception("No ST-Link device found. Please connect an ST-Link.");
    }
    
    System.out.println("Found ST-Link: " + iface.getSerialNumber());
    
    // Create ST-Link and flasher
    StLink stlink = new StLink(iface);
    StLinkFlasher flasher = new StLinkFlasher(stlink);
    
    // Progress callback
    ProgressUpdateInterface progress = new ProgressUpdateInterface() {
      private int lastPercent = -1;
      
      @Override
      public void progressUpdateCallback(int percent) {
        if (percent != lastPercent) {
          lastPercent = percent;
          System.out.printf("\rProgress: %3d%%", percent);
          System.out.flush();
        }
      }
    };
    
    try {
      // Open connection
      System.out.println("Connecting to target...");
      stlink.open();
      
      System.out.printf("ST-Link V%d, JTAG v%d%n", 
          stlink.getStLinkVersion(), stlink.getJtagVersion());
      
      long coreId = stlink.readCoreId();
      System.out.printf("Core ID: 0x%08X%n", coreId);
      
      // Detect chip family
      var family = flasher.detectFamily();
      System.out.printf("Chip: %s (Flash: %dKB)%n", family.name, family.flashSize / 1024);
      System.out.println();
      
      // Flash
      System.out.println("Flashing...");
      flasher.flash(address, data, progress);
      System.out.println();
      
      System.out.println("\nFlash complete!");
      
    } finally {
      stlink.close();
    }
  }

  private static long parseAddress(String addr) throws Exception {
    try {
      if (addr.startsWith("0x") || addr.startsWith("0X")) {
        return Long.parseLong(addr.substring(2), 16);
      }
      return Long.parseLong(addr);
    } catch (NumberFormatException e) {
      throw new Exception("Invalid address: " + addr);
    }
  }
}
