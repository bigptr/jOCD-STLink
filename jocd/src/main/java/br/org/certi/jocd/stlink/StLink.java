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
package br.org.certi.jocd.stlink;

import br.org.certi.jocd.dapaccess.dapexceptions.Error;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ST-Link V2/V3 protocol implementation for debugging and flashing STM32 devices.
 * 
 * This is a minimal implementation focused on flash programming operations.
 */
public class StLink {

  private static final Logger LOGGER = Logger.getLogger(StLink.class.getName());

  // ST-Link commands
  private static final byte STLINK_GET_VERSION = (byte) 0xF1;
  private static final byte STLINK_DEBUG_COMMAND = (byte) 0xF2;
  private static final byte STLINK_DFU_COMMAND = (byte) 0xF3;
  private static final byte STLINK_GET_CURRENT_MODE = (byte) 0xF5;
  
  // Debug commands
  private static final byte STLINK_DEBUG_ENTER = (byte) 0x20;
  private static final byte STLINK_DEBUG_EXIT = (byte) 0x21;
  private static final byte STLINK_DEBUG_READCOREID = (byte) 0x22;
  private static final byte STLINK_DEBUG_GETSTATUS = (byte) 0x01;
  private static final byte STLINK_DEBUG_FORCEDEBUG = (byte) 0x02;
  private static final byte STLINK_DEBUG_RESETSYS = (byte) 0x03;
  private static final byte STLINK_DEBUG_READALLREGS = (byte) 0x04;
  private static final byte STLINK_DEBUG_READREG = (byte) 0x05;
  private static final byte STLINK_DEBUG_WRITEREG = (byte) 0x06;
  private static final byte STLINK_DEBUG_READMEM_32BIT = (byte) 0x07;
  private static final byte STLINK_DEBUG_WRITEMEM_32BIT = (byte) 0x08;
  private static final byte STLINK_DEBUG_RUNCORE = (byte) 0x09;
  private static final byte STLINK_DEBUG_STEPCORE = (byte) 0x0A;
  private static final byte STLINK_DEBUG_SETFP = (byte) 0x0B;
  private static final byte STLINK_DEBUG_WRITEMEM_8BIT = (byte) 0x0D;
  private static final byte STLINK_DEBUG_CLEARFP = (byte) 0x0E;
  private static final byte STLINK_DEBUG_WRITEDEBUGREG = (byte) 0x0F;
  private static final byte STLINK_DEBUG_ENTER_SWD = (byte) 0xA3;
  
  // API v2 commands
  private static final byte STLINK_DEBUG_APIV2_ENTER = (byte) 0x30;
  private static final byte STLINK_DEBUG_APIV2_READ_IDCODES = (byte) 0x31;
  private static final byte STLINK_DEBUG_APIV2_RESETSYS = (byte) 0x32;
  private static final byte STLINK_DEBUG_APIV2_READREG = (byte) 0x33;
  private static final byte STLINK_DEBUG_APIV2_WRITEREG = (byte) 0x34;
  private static final byte STLINK_DEBUG_APIV2_READALLREGS = (byte) 0x3A;
  private static final byte STLINK_DEBUG_APIV2_GETLASTRWSTATUS = (byte) 0x3B;
  private static final byte STLINK_DEBUG_APIV2_DRIVE_NRST = (byte) 0x3C;
  private static final byte STLINK_DEBUG_APIV2_START_TRACE_RX = (byte) 0x40;
  private static final byte STLINK_DEBUG_APIV2_STOP_TRACE_RX = (byte) 0x41;
  private static final byte STLINK_DEBUG_APIV2_GET_TRACE_NB = (byte) 0x42;
  private static final byte STLINK_DEBUG_APIV2_SWD_SET_FREQ = (byte) 0x43;
  private static final byte STLINK_DEBUG_APIV2_READMEM_16BIT = (byte) 0x47;
  private static final byte STLINK_DEBUG_APIV2_WRITEMEM_16BIT = (byte) 0x48;
  
  // Modes
  private static final int STLINK_MODE_DFU = 0x00;
  private static final int STLINK_MODE_MASS = 0x01;
  private static final int STLINK_MODE_DEBUG = 0x02;
  
  // Debug modes
  private static final int STLINK_DEBUG_MODE_SWD = 0xA3;
  
  // NRST drive modes
  private static final byte STLINK_DEBUG_APIV2_DRIVE_NRST_LOW = 0x00;
  private static final byte STLINK_DEBUG_APIV2_DRIVE_NRST_HIGH = 0x01;
  private static final byte STLINK_DEBUG_APIV2_DRIVE_NRST_PULSE = 0x02;
  
  // Status codes
  private static final int STLINK_DEBUG_ERR_OK = 0x80;
  private static final int STLINK_DEBUG_ERR_FAULT = 0x81;
  
  // Cortex-M registers
  private static final long DHCSR = 0xE000EDF0L;
  private static final long DCRSR = 0xE000EDF4L;
  private static final long DCRDR = 0xE000EDF8L;
  private static final long DEMCR = 0xE000EDFCL;
  
  // DHCSR bits
  private static final long DHCSR_DBGKEY = 0xA05F0000L;
  private static final long DHCSR_C_DEBUGEN = 0x00000001L;
  private static final long DHCSR_C_HALT = 0x00000002L;
  private static final long DHCSR_S_HALT = 0x00020000L;
  
  private final StLinkInterface iface;
  private int stlinkVersion;
  private int jtagVersion;
  private boolean isOpen = false;
  
  public StLink(StLinkInterface iface) {
    this.iface = iface;
  }
  
  /**
   * Open connection to ST-Link and enter debug mode.
   */
  public void open() throws Error, TimeoutException {
    // Try to open, with retry if device is in bad state
    Exception lastError = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (attempt > 0) {
          // On retry, close and reopen the interface
          LOGGER.log(Level.INFO, "Retrying ST-Link connection...");
          try {
            iface.close();
            Thread.sleep(100);
          } catch (Exception e) {
            // Ignore
          }
        }
        
        iface.open();
        isOpen = true;
        
        // Get version
        getVersion();
        
        // Leave any current mode - ignore errors as device may be in bad state
        try {
          int mode = getCurrentMode();
          if (mode == STLINK_MODE_DFU) {
            leaveDfuMode();
          } else if (mode == STLINK_MODE_DEBUG) {
            leaveDebugMode();
          }
        } catch (Exception e) {
          LOGGER.log(Level.FINE, "Could not leave current mode (may be normal): " + e.getMessage());
        }
        
        // Enter SWD debug mode
        enterSwdMode();
        
        LOGGER.log(Level.INFO, "ST-Link opened successfully. Version: V" + stlinkVersion + 
            ", JTAG: " + jtagVersion);
        return;
        
      } catch (Exception e) {
        lastError = e;
        LOGGER.log(Level.WARNING, "ST-Link open attempt " + (attempt + 1) + " failed: " + e.getMessage());
      }
    }
    
    // All attempts failed
    if (lastError instanceof Error) {
      throw (Error) lastError;
    } else if (lastError instanceof TimeoutException) {
      throw (TimeoutException) lastError;
    } else {
      throw new Error("Failed to open ST-Link: " + lastError.getMessage());
    }
  }
  
  /**
   * Close connection to ST-Link.
   */
  public void close() {
    if (isOpen) {
      try {
        leaveDebugMode();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error leaving debug mode: " + e.getMessage());
      }
      iface.close();
      isOpen = false;
    }
  }
  
  // GET_VERSION_EXT command for V3
  private static final byte STLINK_GET_VERSION_EXT = (byte) 0xFB;
  
  /**
   * Get ST-Link version information.
   */
  private void getVersion() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_GET_VERSION;
    
    byte[] response = iface.transfer(cmd, 6);
    
    stlinkVersion = (response[0] >> 4) & 0x0F;
    jtagVersion = ((response[0] & 0x0F) << 2) | ((response[1] >> 6) & 0x03);
    
    // For V3 and later, use extended version command
    if (stlinkVersion >= 3) {
      cmd = new byte[16];
      cmd[0] = STLINK_GET_VERSION_EXT;
      
      response = iface.transfer(cmd, 12);
      // Extended format: [0]=HW, [1]=SWIM, [2]=JTAG, [3]=MSC, [4]=Bridge, [5]=Power
      stlinkVersion = response[0] & 0xFF;
      jtagVersion = response[2] & 0xFF;
    }
  }
  
  /**
   * Get current ST-Link mode.
   */
  private int getCurrentMode() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_GET_CURRENT_MODE;
    
    byte[] response = iface.transfer(cmd, 2);
    return response[0] & 0xFF;
  }
  
  /**
   * Leave DFU mode.
   */
  private void leaveDfuMode() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DFU_COMMAND;
    cmd[1] = 0x07; // DFU_EXIT
    
    iface.transfer(cmd, 0);
  }
  
  /**
   * Leave debug mode.
   */
  private void leaveDebugMode() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_EXIT;
    
    iface.transfer(cmd, 0);
  }
  
  /**
   * Enter SWD debug mode with connect-under-reset to handle hung targets.
   */
  private void enterSwdMode() throws Error, TimeoutException {
    // For V3/API v2, use connect-under-reset to handle hung MCUs
    if (stlinkVersion >= 3 || jtagVersion >= 28) {
      // First, assert NRST (hold target in reset)
      byte[] cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_APIV2_DRIVE_NRST;
      cmd[2] = STLINK_DEBUG_APIV2_DRIVE_NRST_LOW;
      try {
        iface.transfer(cmd, 2);
        Thread.sleep(50); // Give time for reset to take effect
      } catch (Exception e) {
        LOGGER.log(Level.FINE, "Could not assert NRST: " + e.getMessage());
      }
      
      // Enter SWD mode while target is in reset
      cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_APIV2_ENTER;
      cmd[2] = (byte) STLINK_DEBUG_MODE_SWD;
      
      byte[] response = iface.transfer(cmd, 2);
      checkStatus(response);
      
      // Halt the CPU before releasing reset
      cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_FORCEDEBUG;
      try {
        iface.transfer(cmd, 2);
      } catch (Exception e) {
        LOGGER.log(Level.FINE, "Could not halt CPU: " + e.getMessage());
      }
      
      // Now release NRST - CPU should stay halted
      cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_APIV2_DRIVE_NRST;
      cmd[2] = STLINK_DEBUG_APIV2_DRIVE_NRST_HIGH;
      try {
        iface.transfer(cmd, 2);
        Thread.sleep(10); // Small delay after releasing reset
      } catch (Exception e) {
        LOGGER.log(Level.FINE, "Could not release NRST: " + e.getMessage());
      }
    } else {
      // API v1
      byte[] cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_ENTER;
      cmd[2] = (byte) STLINK_DEBUG_MODE_SWD;
      
      byte[] response = iface.transfer(cmd, 2);
      checkStatus(response);
    }
  }
  
  /**
   * Read the target's core ID.
   * For V3, we read the CPUID register directly as the READCOREID command may not work.
   */
  public long readCoreId() throws Error, TimeoutException {
    // Try reading CPUID register at 0xE000ED00
    try {
      return readMem32(0xE000ED00L);
    } catch (Error e) {
      // Fallback to READCOREID command
      byte[] cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_READCOREID;
      
      byte[] response = iface.transfer(cmd, 4);
      return bytesToLong(response, 0, 4);
    }
  }
  
  /**
   * Check if we should use API v2 commands.
   */
  private boolean useApiV2() {
    return stlinkVersion >= 3 || jtagVersion >= 28;
  }
  
  /**
   * Halt the CPU.
   */
  public void halt() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_FORCEDEBUG;  // 0x02 - works for both API v1 and v2
    
    byte[] response = iface.transfer(cmd, 2);
    // Don't check status strictly - some targets return non-0x80 when already halted
    int status = response[0] & 0xFF;
    if (status != STLINK_DEBUG_ERR_OK && status != 0x00 && status != 0x01) {
      // Only fail on actual errors, not on "already halted" type responses
      LOGGER.log(Level.FINE, "Halt returned status: 0x" + String.format("%02X", status));
    }
  }
  
  /**
   * Run the CPU.
   */
  public void run() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_RUNCORE;  // 0x09 - works for both API v1 and v2
    
    byte[] response = iface.transfer(cmd, 2);
    checkStatus(response);
  }
  
  /**
   * Reset the target system using software reset.
   */
  public void reset() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    
    if (useApiV2()) {
      cmd[1] = STLINK_DEBUG_APIV2_RESETSYS;
    } else {
      cmd[1] = STLINK_DEBUG_RESETSYS;
    }
    
    byte[] response = iface.transfer(cmd, 2);
    checkStatus(response);
  }
  
  /**
   * Hardware reset using NRST pin pulse, then run.
   */
  public void hardwareResetAndRun() throws Error, TimeoutException {
    if (useApiV2()) {
      // Use NRST pulse for hardware reset
      byte[] cmd = new byte[16];
      cmd[0] = STLINK_DEBUG_COMMAND;
      cmd[1] = STLINK_DEBUG_APIV2_DRIVE_NRST;
      cmd[2] = STLINK_DEBUG_APIV2_DRIVE_NRST_PULSE;
      
      byte[] response = iface.transfer(cmd, 2);
      checkStatus(response);
    } else {
      // Fallback to software reset + run
      reset();
      run();
    }
  }
  
  /**
   * Drive NRST pin.
   */
  public void driveNrst(boolean assert_) throws Error, TimeoutException {
    if (!useApiV2()) {
      throw new Error("NRST control requires API v2");
    }
    
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_APIV2_DRIVE_NRST;
    cmd[2] = assert_ ? STLINK_DEBUG_APIV2_DRIVE_NRST_LOW : STLINK_DEBUG_APIV2_DRIVE_NRST_HIGH;
    
    byte[] response = iface.transfer(cmd, 2);
    checkStatus(response);
  }
  
  /**
   * Read 32-bit memory.
   */
  public long readMem32(long address) throws Error, TimeoutException {
    byte[] data = readMemory(address, 4);
    return bytesToLong(data, 0, 4);
  }
  
  /**
   * Write 32-bit memory.
   */
  public void writeMem32(long address, long value) throws Error, TimeoutException {
    byte[] data = new byte[4];
    longToBytes(value, data, 0, 4);
    writeMemory(address, data);
  }
  
  // Maximum transfer size per pyOCD (6144 for 32-bit, 64 for 8-bit)
  private static final int MAX_TRANSFER_SIZE = 1024;
  
  // Command for getting last read/write status
  private static final byte STLINK_DEBUG_APIV2_GETLASTRWSTATUS2 = (byte) 0x3E;
  
  /**
   * Read memory block.
   */
  public byte[] readMemory(long address, int length) throws Error, TimeoutException {
    // For small reads, do it directly
    if (length <= MAX_TRANSFER_SIZE) {
      return readMemoryChunk(address, length);
    }
    
    // For larger reads, chunk it
    byte[] result = new byte[length];
    int offset = 0;
    while (offset < length) {
      int chunkSize = Math.min(MAX_TRANSFER_SIZE, length - offset);
      byte[] chunk = readMemoryChunk(address + offset, chunkSize);
      System.arraycopy(chunk, 0, result, offset, chunk.length);
      offset += chunk.length;
    }
    return result;
  }
  
  /**
   * Read a single chunk of memory (up to MAX_TRANSFER_SIZE).
   */
  private byte[] readMemoryChunk(long address, int length) throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_READMEM_32BIT;
    longToBytes(address, cmd, 2, 4);
    cmd[6] = (byte) (length & 0xFF);
    cmd[7] = (byte) ((length >> 8) & 0xFF);
    // cmd[8] = APSEL (0 for default)
    // cmd[9-11] = CSW[31:8] (0 for default)
    
    byte[] data = iface.transferIn(cmd, length);
    
    // Skip status check for simple reads - it causes USB issues on macOS
    // The data itself will indicate if there was a problem
    
    return data;
  }
  
  /**
   * Write memory block (must be 32-bit aligned).
   */
  public void writeMemory(long address, byte[] data) throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_WRITEMEM_32BIT;
    longToBytes(address, cmd, 2, 4);
    cmd[6] = (byte) (data.length & 0xFF);
    cmd[7] = (byte) ((data.length >> 8) & 0xFF);
    
    iface.transferOut(cmd, data);
    
    // Skip status check - causes USB issues on macOS with rapid transfers
  }
  
  /**
   * Write memory block (8-bit access, for non-aligned writes).
   */
  public void writeMemory8(long address, byte[] data) throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_WRITEMEM_8BIT;
    longToBytes(address, cmd, 2, 4);
    cmd[6] = (byte) (data.length & 0xFF);
    cmd[7] = (byte) ((data.length >> 8) & 0xFF);
    
    iface.transferOut(cmd, data);
    
    // Skip status check - causes USB issues on macOS with rapid transfers
  }
  
  /**
   * Check last read/write status (API v2).
   */
  private void checkLastRwStatus() throws Error, TimeoutException {
    byte[] cmd = new byte[16];
    cmd[0] = STLINK_DEBUG_COMMAND;
    cmd[1] = STLINK_DEBUG_APIV2_GETLASTRWSTATUS;
    
    byte[] response = iface.transfer(cmd, 2);
    checkStatus(response);
  }
  
  // Additional status codes
  private static final int STLINK_JTAG_OK = 0x80;
  private static final int STLINK_SWD_AP_WAIT = 0x10;
  private static final int STLINK_SWD_DP_WAIT = 0x14;
  
  /**
   * Check status response.
   */
  private void checkStatus(byte[] response) throws Error {
    int status = response[0] & 0xFF;
    // 0x80 = OK, 0x00 = also OK for some commands
    if (status != STLINK_DEBUG_ERR_OK && status != 0x00) {
      throw new Error("ST-Link error: 0x" + String.format("%02X", status));
    }
  }
  
  /**
   * Check status response, allowing certain non-fatal statuses.
   */
  private void checkStatusAllowWait(byte[] response) throws Error {
    int status = response[0] & 0xFF;
    // Allow OK, zero, and WAIT statuses
    if (status != STLINK_DEBUG_ERR_OK && status != 0x00 && 
        status != STLINK_SWD_AP_WAIT && status != STLINK_SWD_DP_WAIT) {
      throw new Error("ST-Link error: 0x" + String.format("%02X", status));
    }
  }
  
  /**
   * Get the serial number.
   */
  public String getSerialNumber() {
    return iface.getSerialNumber();
  }
  
  /**
   * Get ST-Link version.
   */
  public int getStLinkVersion() {
    return stlinkVersion;
  }
  
  /**
   * Get JTAG version.
   */
  public int getJtagVersion() {
    return jtagVersion;
  }
  
  // Utility methods
  
  private static long bytesToLong(byte[] data, int offset, int length) {
    long result = 0;
    for (int i = 0; i < length; i++) {
      result |= ((long) (data[offset + i] & 0xFF)) << (i * 8);
    }
    return result;
  }
  
  private static void longToBytes(long value, byte[] data, int offset, int length) {
    for (int i = 0; i < length; i++) {
      data[offset + i] = (byte) ((value >> (i * 8)) & 0xFF);
    }
  }
}
