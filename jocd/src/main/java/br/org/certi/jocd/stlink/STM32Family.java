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

/**
 * STM32 chip family definitions with flash parameters.
 */
public enum STM32Family {
  
  // STM32F4 series (e.g., STM32F446RE)
  // Flash: 512KB, sectors with varying sizes
  // Sectors 0-3: 16KB, Sector 4: 64KB, Sectors 5-7: 128KB
  STM32F4(
      "STM32F4",
      0x40023C00L,  // FLASH_BASE
      new int[] { 16*1024, 16*1024, 16*1024, 16*1024, 64*1024, 128*1024, 128*1024, 128*1024 },
      0x08000000L,  // FLASH_START
      512 * 1024,   // FLASH_SIZE (512KB for F446RE)
      2             // PSIZE for x32 parallelism
  ),
  
  // STM32F7 series (e.g., STM32F767ZI)
  // Flash: 2MB, sectors with varying sizes
  // Sectors 0-3: 32KB, Sector 4: 128KB, Sectors 5-11: 256KB
  STM32F7(
      "STM32F7",
      0x40023C00L,  // FLASH_BASE (same as F4)
      new int[] { 32*1024, 32*1024, 32*1024, 32*1024, 128*1024, 256*1024, 256*1024, 256*1024,
                  256*1024, 256*1024, 256*1024, 256*1024 },
      0x08000000L,  // FLASH_START
      2 * 1024 * 1024,  // FLASH_SIZE (2MB)
      2             // PSIZE for x32 parallelism
  ),
  
  // STM32G4 series (e.g., STM32G474RE)
  // Flash: 512KB, page-based (2KB pages), dual bank
  // Uses different flash controller than F4/F7
  STM32G4(
      "STM32G4",
      0x40022000L,  // FLASH_BASE (different from F4/F7!)
      null,         // Page-based, not sector-based
      0x08000000L,  // FLASH_START
      512 * 1024,   // FLASH_SIZE (512KB for G474RE)
      0             // Not used for G4
  );
  
  public final String name;
  public final long flashBase;
  public final int[] sectorSizes;  // null for page-based flash
  public final long flashStart;
  public final int flashSize;
  public final int psize;
  
  // Flash register offsets (F4/F7 style)
  public static final int FLASH_ACR_OFFSET = 0x00;
  public static final int FLASH_KEYR_OFFSET = 0x04;
  public static final int FLASH_OPTKEYR_OFFSET = 0x08;
  public static final int FLASH_SR_OFFSET = 0x0C;
  public static final int FLASH_CR_OFFSET = 0x10;
  
  // G4 flash register offsets (different layout)
  public static final int G4_FLASH_ACR_OFFSET = 0x00;
  public static final int G4_FLASH_KEYR_OFFSET = 0x08;
  public static final int G4_FLASH_SR_OFFSET = 0x10;
  public static final int G4_FLASH_CR_OFFSET = 0x14;
  
  // Flash keys (same for all)
  public static final long FLASH_KEY1 = 0x45670123L;
  public static final long FLASH_KEY2 = 0xCDEF89ABL;
  
  // Page size for G4
  public static final int G4_PAGE_SIZE = 2048;
  
  STM32Family(String name, long flashBase, int[] sectorSizes, 
              long flashStart, int flashSize, int psize) {
    this.name = name;
    this.flashBase = flashBase;
    this.sectorSizes = sectorSizes;
    this.flashStart = flashStart;
    this.flashSize = flashSize;
    this.psize = psize;
  }
  
  /**
   * Check if this family uses page-based flash (like G4) vs sector-based (like F4/F7).
   */
  public boolean isPageBased() {
    return sectorSizes == null;
  }
  
  /**
   * Detect STM32 family from DBGMCU_IDCODE.
   * 
   * @param idcode The DBGMCU_IDCODE value
   * @return The detected family, or null if unknown
   */
  public static STM32Family fromIdcode(long idcode) {
    // DEV_ID is in bits [11:0]
    int devId = (int) (idcode & 0xFFF);
    
    switch (devId) {
      // STM32F4 series
      case 0x421: // STM32F446
      case 0x423: // STM32F401xB/C
      case 0x431: // STM32F411
      case 0x433: // STM32F401xD/E
      case 0x441: // STM32F412
      case 0x458: // STM32F410
        return STM32F4;
        
      // STM32F7 series
      case 0x449: // STM32F74x/F75x
      case 0x451: // STM32F76x/F77x
      case 0x452: // STM32F72x/F73x
        return STM32F7;
        
      // STM32G4 series
      case 0x468: // STM32G431/G441
      case 0x469: // STM32G47x/G48x
      case 0x479: // STM32G491/G4A1
        return STM32G4;
        
      default:
        return null;
    }
  }
  
  /**
   * Get sector number for a given address (for sector-based flash).
   */
  public int getSectorForAddress(long address) {
    if (isPageBased()) {
      throw new IllegalStateException("Use getPageForAddress for page-based flash");
    }
    
    long offset = address - flashStart;
    if (offset < 0 || offset >= flashSize) {
      throw new IllegalArgumentException("Address outside flash region");
    }
    
    long currentOffset = 0;
    for (int i = 0; i < sectorSizes.length; i++) {
      if (offset < currentOffset + sectorSizes[i]) {
        return i;
      }
      currentOffset += sectorSizes[i];
    }
    
    // For addresses beyond defined sectors, calculate based on last sector size
    int lastSectorSize = sectorSizes[sectorSizes.length - 1];
    return sectorSizes.length + (int) ((offset - currentOffset) / lastSectorSize);
  }
  
  /**
   * Get page number for a given address (for page-based flash like G4).
   */
  public int getPageForAddress(long address) {
    if (!isPageBased()) {
      throw new IllegalStateException("Use getSectorForAddress for sector-based flash");
    }
    
    long offset = address - flashStart;
    if (offset < 0 || offset >= flashSize) {
      throw new IllegalArgumentException("Address outside flash region");
    }
    
    return (int) (offset / G4_PAGE_SIZE);
  }
}
