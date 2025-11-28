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
import br.org.certi.jocd.tools.ProgressUpdateInterface;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flash programmer for STM32 series using ST-Link protocol.
 * 
 * Supports STM32F4, STM32F7, and STM32G4 families.
 */
public class StLinkFlasher {

  private static final Logger LOGGER = Logger.getLogger(StLinkFlasher.class.getName());

  // DBGMCU register for chip identification
  private static final long DBGMCU_IDCODE = 0xE0042000L;
  
  // Flash keys (same for all families)
  private static final long FLASH_KEY1 = 0x45670123L;
  private static final long FLASH_KEY2 = 0xCDEF89ABL;

  // F4/F7 Flash CR bits
  private static final long FLASH_CR_PG = 1 << 0;      // Programming
  private static final long FLASH_CR_SER = 1 << 1;     // Sector erase
  private static final long FLASH_CR_MER = 1 << 2;     // Mass erase
  private static final long FLASH_CR_SNB_SHIFT = 3;    // Sector number shift
  private static final long FLASH_CR_SNB_MASK = 0x1F << 3;
  private static final long FLASH_CR_PSIZE_X32 = 2 << 8; // 32-bit parallelism
  private static final long FLASH_CR_STRT = 1 << 16;   // Start
  private static final long FLASH_CR_LOCK = 1 << 31;   // Lock

  // F4/F7 Flash SR bits
  private static final long FLASH_SR_BSY = 1 << 16;    // Busy
  private static final long FLASH_SR_OPERR = 1 << 1;   // Operation error
  private static final long FLASH_SR_WRPERR = 1 << 4;  // Write protection error
  private static final long FLASH_SR_PGAERR = 1 << 5;  // Programming alignment error
  private static final long FLASH_SR_PGPERR = 1 << 6;  // Programming parallelism error
  private static final long FLASH_SR_ERSERR = 1 << 7;  // Erase sequence error
  private static final long FLASH_SR_ERROR_MASK = FLASH_SR_OPERR | FLASH_SR_WRPERR | 
      FLASH_SR_PGAERR | FLASH_SR_PGPERR | FLASH_SR_ERSERR;

  // G4 Flash CR bits (different layout!)
  private static final long G4_FLASH_CR_PG = 1 << 0;       // Programming
  private static final long G4_FLASH_CR_PER = 1 << 1;      // Page erase
  private static final long G4_FLASH_CR_MER1 = 1 << 2;     // Bank 1 mass erase
  private static final long G4_FLASH_CR_PNB_SHIFT = 3;     // Page number shift
  private static final long G4_FLASH_CR_PNB_MASK = 0x7F << 3;
  private static final long G4_FLASH_CR_STRT = 1 << 16;    // Start
  private static final long G4_FLASH_CR_LOCK = 1 << 31;    // Lock
  
  // G4 Flash SR bits
  private static final long G4_FLASH_SR_BSY = 1 << 16;     // Busy
  private static final long G4_FLASH_SR_PROGERR = 1 << 3;  // Programming error
  private static final long G4_FLASH_SR_WRPERR = 1 << 4;   // Write protection error
  private static final long G4_FLASH_SR_PGAERR = 1 << 5;   // Programming alignment error
  private static final long G4_FLASH_SR_SIZERR = 1 << 6;   // Size error
  private static final long G4_FLASH_SR_PGSERR = 1 << 7;   // Programming sequence error
  private static final long G4_FLASH_SR_ERROR_MASK = G4_FLASH_SR_PROGERR | G4_FLASH_SR_WRPERR |
      G4_FLASH_SR_PGAERR | G4_FLASH_SR_SIZERR | G4_FLASH_SR_PGSERR;

  private final StLink stlink;
  private STM32Family family;
  private static final int WRITE_CHUNK_SIZE = 1024; // Write in 1KB chunks
  private static final int TIMEOUT_MS = 10000; // 10 second timeout for flash operations

  public StLinkFlasher(StLink stlink) {
    this.stlink = stlink;
  }
  
  /**
   * Detect the connected chip family.
   */
  public STM32Family detectFamily() throws Error, TimeoutException {
    long idcode = stlink.readMem32(DBGMCU_IDCODE);
    LOGGER.log(Level.INFO, String.format("DBGMCU_IDCODE: 0x%08X", idcode));
    
    family = STM32Family.fromIdcode(idcode);
    if (family == null) {
      throw new Error("Unknown STM32 device (IDCODE: 0x" + Long.toHexString(idcode) + ")");
    }
    
    LOGGER.log(Level.INFO, "Detected " + family.name + " family");
    return family;
  }
  
  /**
   * Get flash register addresses based on family.
   */
  private long getFlashKeyr() {
    if (family == STM32Family.STM32G4) {
      return family.flashBase + STM32Family.G4_FLASH_KEYR_OFFSET;
    }
    return family.flashBase + STM32Family.FLASH_KEYR_OFFSET;
  }
  
  private long getFlashSr() {
    if (family == STM32Family.STM32G4) {
      return family.flashBase + STM32Family.G4_FLASH_SR_OFFSET;
    }
    return family.flashBase + STM32Family.FLASH_SR_OFFSET;
  }
  
  private long getFlashCr() {
    if (family == STM32Family.STM32G4) {
      return family.flashBase + STM32Family.G4_FLASH_CR_OFFSET;
    }
    return family.flashBase + STM32Family.FLASH_CR_OFFSET;
  }

  /**
   * Flash binary data to the target at the specified address.
   * 
   * @param address Start address (must be within flash region)
   * @param data Binary data to flash
   * @param progress Optional progress callback
   */
  public void flash(long address, byte[] data, ProgressUpdateInterface progress) 
      throws Error, TimeoutException, InterruptedException {
    
    // Auto-detect chip family if not already done
    if (family == null) {
      detectFamily();
    }
    
    LOGGER.log(Level.INFO, String.format("Flashing %d bytes to 0x%08X (%s)", 
        data.length, address, family.name));
    
    // Validate address
    if (address < family.flashStart || address >= family.flashStart + family.flashSize) {
      throw new Error("Address 0x" + Long.toHexString(address) + " is outside flash region");
    }
    
    // Halt the CPU
    stlink.halt();
    Thread.sleep(10);
    
    // Unlock flash
    unlockFlash();
    
    // Erase flash (sector-based or page-based depending on family)
    if (family.isPageBased()) {
      // G4 uses page-based erase
      int startPage = family.getPageForAddress(address);
      int endPage = family.getPageForAddress(address + data.length - 1);
      
      LOGGER.log(Level.INFO, "Erasing pages " + startPage + " to " + endPage);
      
      // Use mass erase for G4 to avoid USB issues with many page erases
      if (progress != null) {
        progress.progressUpdateCallback(5);
      }
      massEraseG4();
      if (progress != null) {
        progress.progressUpdateCallback(30);
      }
    } else {
      // F4/F7 use sector-based erase
      int startSector = family.getSectorForAddress(address);
      int endSector = family.getSectorForAddress(address + data.length - 1);
      
      LOGGER.log(Level.INFO, "Erasing sectors " + startSector + " to " + endSector);
      
      for (int sector = startSector; sector <= endSector; sector++) {
        if (progress != null) {
          int pct = (int) ((float) (sector - startSector) / (endSector - startSector + 1) * 30);
          progress.progressUpdateCallback(pct);
        }
        eraseSector(sector);
      }
    }
    
    // Clear any error flags
    clearErrors();
    
    // Program flash
    LOGGER.log(Level.INFO, "Programming flash...");
    programFlash(address, data, progress);
    
    // Lock flash
    lockFlash();
    
    // Verify flash contents
    LOGGER.log(Level.INFO, "Verifying...");
    verifyFlash(address, data, progress);
    
    LOGGER.log(Level.INFO, "Flash complete!");
    
    if (progress != null) {
      progress.progressUpdateCallback(100);
    }
    
    // Hardware reset the target to run the new firmware
    LOGGER.log(Level.INFO, "Resetting target...");
    stlink.hardwareResetAndRun();
  }

  /**
   * Unlock the flash for programming.
   */
  private void unlockFlash() throws Error, TimeoutException {
    long lockBit = (family == STM32Family.STM32G4) ? G4_FLASH_CR_LOCK : FLASH_CR_LOCK;
    long cr = stlink.readMem32(getFlashCr());
    
    if ((cr & lockBit) != 0) {
      // Flash is locked, unlock it
      stlink.writeMem32(getFlashKeyr(), FLASH_KEY1);
      stlink.writeMem32(getFlashKeyr(), FLASH_KEY2);
      
      // Verify unlock
      cr = stlink.readMem32(getFlashCr());
      if ((cr & lockBit) != 0) {
        throw new Error("Failed to unlock flash");
      }
    }
    
    LOGGER.log(Level.FINE, "Flash unlocked");
  }

  /**
   * Lock the flash.
   */
  private void lockFlash() throws Error, TimeoutException {
    long lockBit = (family == STM32Family.STM32G4) ? G4_FLASH_CR_LOCK : FLASH_CR_LOCK;
    long cr = stlink.readMem32(getFlashCr());
    cr |= lockBit;
    stlink.writeMem32(getFlashCr(), cr);
  }

  /**
   * Erase a flash sector (for F4/F7).
   */
  private void eraseSector(int sector) throws Error, TimeoutException, InterruptedException {
    LOGGER.log(Level.FINE, "Erasing sector " + sector);
    
    // Wait for any previous operation
    waitForFlash();
    
    // Set sector erase
    long cr = FLASH_CR_SER | FLASH_CR_PSIZE_X32;
    cr |= ((long) sector << FLASH_CR_SNB_SHIFT) & FLASH_CR_SNB_MASK;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Start erase
    cr |= FLASH_CR_STRT;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Wait for completion
    waitForFlash();
    
    // Clear SER bit
    cr = stlink.readMem32(getFlashCr());
    cr &= ~(FLASH_CR_SER | FLASH_CR_SNB_MASK);
    stlink.writeMem32(getFlashCr(), cr);
    
    // Check for errors
    checkErrors();
  }
  
  /**
   * Mass erase bank 1 (for G4) - single operation instead of page-by-page.
   */
  private void massEraseG4() throws Error, TimeoutException, InterruptedException {
    LOGGER.log(Level.INFO, "Mass erasing flash bank 1...");
    
    // Wait for any previous operation
    waitForFlash();
    
    // Set mass erase for bank 1
    long cr = G4_FLASH_CR_MER1;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Start erase
    cr |= G4_FLASH_CR_STRT;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Wait for completion - mass erase takes longer
    waitForFlash();
    
    // Clear MER1 bit
    stlink.writeMem32(getFlashCr(), 0);
    
    // Check for errors
    checkErrors();
    
    LOGGER.log(Level.INFO, "Mass erase complete");
  }
  
  /**
   * Erase a flash page (for G4).
   */
  private void erasePage(int page) throws Error, TimeoutException, InterruptedException {
    LOGGER.log(Level.FINE, "Erasing page " + page);
    
    // Wait for any previous operation
    waitForFlash();
    
    // Set page erase
    long cr = G4_FLASH_CR_PER;
    cr |= ((long) page << G4_FLASH_CR_PNB_SHIFT) & G4_FLASH_CR_PNB_MASK;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Start erase
    cr |= G4_FLASH_CR_STRT;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Wait for completion
    waitForFlash();
    
    // Clear PER bit
    cr = stlink.readMem32(getFlashCr());
    cr &= ~(G4_FLASH_CR_PER | G4_FLASH_CR_PNB_MASK);
    stlink.writeMem32(getFlashCr(), cr);
    
    // Check for errors
    checkErrors();
  }

  /**
   * Program flash memory.
   */
  private void programFlash(long address, byte[] data, ProgressUpdateInterface progress) 
      throws Error, TimeoutException, InterruptedException {
    
    // Enable programming
    long cr;
    if (family == STM32Family.STM32G4) {
      cr = G4_FLASH_CR_PG;
    } else {
      cr = FLASH_CR_PG | FLASH_CR_PSIZE_X32;
    }
    stlink.writeMem32(getFlashCr(), cr);
    
    // G4 requires 8-byte (double-word) alignment, F4/F7 use 4-byte
    int alignmentSize = (family == STM32Family.STM32G4) ? 8 : 4;
    
    // Pad data to alignment boundary
    int paddedLength = ((data.length + alignmentSize - 1) / alignmentSize) * alignmentSize;
    byte[] paddedData;
    if (paddedLength != data.length) {
      paddedData = new byte[paddedLength];
      System.arraycopy(data, 0, paddedData, 0, data.length);
      // Pad with 0xFF (erased flash value)
      for (int i = data.length; i < paddedLength; i++) {
        paddedData[i] = (byte) 0xFF;
      }
    } else {
      paddedData = data;
    }
    
    int offset = 0;
    while (offset < paddedData.length) {
      int chunkSize = Math.min(WRITE_CHUNK_SIZE, paddedData.length - offset);
      
      // Ensure chunk is aligned
      chunkSize = (chunkSize / alignmentSize) * alignmentSize;
      if (chunkSize == 0) chunkSize = alignmentSize;
      
      byte[] chunk = new byte[chunkSize];
      System.arraycopy(paddedData, offset, chunk, 0, chunkSize);
      stlink.writeMemory(address + offset, chunk);
      
      // Small delay between writes for USB stability on macOS
      Thread.sleep(2);
      
      offset += chunkSize;
      
      if (progress != null) {
        // Progress: 30% for erase, 50% for program, 20% for verify
        int pct = 30 + (int) ((float) offset / paddedData.length * 50);
        progress.progressUpdateCallback(pct);
      }
      
      // Check for errors periodically
      if (offset % (16 * 1024) == 0) {
        checkErrors();
        Thread.sleep(5); // Extra delay after error check
      }
    }
    
    // Wait for last write to complete
    waitForFlash();
    
    // Disable programming
    long pgBit = (family == STM32Family.STM32G4) ? G4_FLASH_CR_PG : FLASH_CR_PG;
    cr = stlink.readMem32(getFlashCr());
    cr &= ~pgBit;
    stlink.writeMem32(getFlashCr(), cr);
    
    // Check for errors
    checkErrors();
  }

  /**
   * Verify flash contents.
   */
  private void verifyFlash(long address, byte[] data, ProgressUpdateInterface progress) 
      throws Error, TimeoutException {
    
    int offset = 0;
    while (offset < data.length) {
      int chunkSize = Math.min(WRITE_CHUNK_SIZE, data.length - offset);
      
      byte[] readData = stlink.readMemory(address + offset, chunkSize);
      
      for (int i = 0; i < chunkSize; i++) {
        if (readData[i] != data[offset + i]) {
          throw new Error(String.format(
              "Verification failed at 0x%08X: expected 0x%02X, got 0x%02X",
              address + offset + i, data[offset + i] & 0xFF, readData[i] & 0xFF));
        }
      }
      
      offset += chunkSize;
      
      if (progress != null) {
        int pct = 80 + (int) ((float) offset / data.length * 20);
        progress.progressUpdateCallback(pct);
      }
    }
  }

  /**
   * Wait for flash operation to complete.
   */
  private void waitForFlash() throws Error, TimeoutException, InterruptedException {
    long startTime = System.currentTimeMillis();
    long bsyBit = (family == STM32Family.STM32G4) ? G4_FLASH_SR_BSY : FLASH_SR_BSY;
    
    // Initial delay before first poll - give flash time to start
    Thread.sleep(5);
    
    while (true) {
      long sr = stlink.readMem32(getFlashSr());
      
      if ((sr & bsyBit) == 0) {
        return;
      }
      
      if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
        throw new TimeoutException("Flash operation timed out");
      }
      
      // Longer delay between polls to avoid overwhelming USB
      Thread.sleep(10);
    }
  }

  /**
   * Check for flash errors.
   */
  private void checkErrors() throws Error, TimeoutException {
    long sr = stlink.readMem32(getFlashSr());
    
    if (family == STM32Family.STM32G4) {
      if ((sr & G4_FLASH_SR_ERROR_MASK) != 0) {
        String errorMsg = "Flash error: ";
        if ((sr & G4_FLASH_SR_WRPERR) != 0) errorMsg += "Write protection ";
        if ((sr & G4_FLASH_SR_PGAERR) != 0) errorMsg += "Alignment ";
        if ((sr & G4_FLASH_SR_PROGERR) != 0) errorMsg += "Programming ";
        if ((sr & G4_FLASH_SR_SIZERR) != 0) errorMsg += "Size ";
        if ((sr & G4_FLASH_SR_PGSERR) != 0) errorMsg += "Sequence ";
        
        clearErrors();
        throw new Error(errorMsg);
      }
    } else {
      if ((sr & FLASH_SR_ERROR_MASK) != 0) {
        String errorMsg = "Flash error: ";
        if ((sr & FLASH_SR_WRPERR) != 0) errorMsg += "Write protection ";
        if ((sr & FLASH_SR_PGAERR) != 0) errorMsg += "Alignment ";
        if ((sr & FLASH_SR_PGPERR) != 0) errorMsg += "Parallelism ";
        if ((sr & FLASH_SR_ERSERR) != 0) errorMsg += "Erase sequence ";
        if ((sr & FLASH_SR_OPERR) != 0) errorMsg += "Operation ";
        
        clearErrors();
        throw new Error(errorMsg);
      }
    }
  }

  /**
   * Clear flash error flags.
   */
  private void clearErrors() throws Error, TimeoutException {
    if (family == STM32Family.STM32G4) {
      stlink.writeMem32(getFlashSr(), G4_FLASH_SR_ERROR_MASK);
    } else {
      stlink.writeMem32(getFlashSr(), FLASH_SR_ERROR_MASK);
    }
  }

  /**
   * Reset the target and run.
   */
  public void resetAndRun() throws Error, TimeoutException, InterruptedException {
    stlink.reset();
    Thread.sleep(10);
    stlink.run();
  }
  
  /**
   * Get the detected chip family.
   */
  public STM32Family getFamily() {
    return family;
  }
}
