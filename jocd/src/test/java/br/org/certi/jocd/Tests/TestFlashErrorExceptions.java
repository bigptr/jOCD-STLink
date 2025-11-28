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
package br.org.certi.jocd.Tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import br.org.certi.jocd.dapaccess.dapexceptions.Error;
import br.org.certi.jocd.target.stm32f7.FlashProgrammingError;
import br.org.certi.jocd.target.stm32f7.FlashSequenceError;
import br.org.certi.jocd.target.stm32f7.FlashTimeoutError;
import br.org.certi.jocd.target.stm32f7.FlashWriteProtectionError;
import org.junit.Test;

/**
 * Unit tests for flash error exception classes.
 * 
 * These tests verify that the flash-specific exception classes are properly
 * defined and can be instantiated with appropriate error messages.
 */
public class TestFlashErrorExceptions {

  /**
   * Test FlashWriteProtectionError exception.
   */
  @Test
  public void testFlashWriteProtectionError() {
    // Test default constructor
    FlashWriteProtectionError error1 = new FlashWriteProtectionError();
    assertNotNull(error1);
    assertNotNull(error1.getMessage());
    assertTrue(error1.getMessage().contains("write protection"));
    
    // Test constructor with custom message
    String customMessage = "Custom write protection error";
    FlashWriteProtectionError error2 = new FlashWriteProtectionError(customMessage);
    assertNotNull(error2);
    assertEquals(customMessage, error2.getMessage());
    
    // Verify it extends Error
    assertTrue(error1 instanceof Error);
  }

  /**
   * Test FlashSequenceError exception.
   */
  @Test
  public void testFlashSequenceError() {
    // Test default constructor
    FlashSequenceError error1 = new FlashSequenceError();
    assertNotNull(error1);
    assertNotNull(error1.getMessage());
    assertTrue(error1.getMessage().contains("sequence"));
    
    // Test constructor with custom message
    String customMessage = "Custom sequence error";
    FlashSequenceError error2 = new FlashSequenceError(customMessage);
    assertNotNull(error2);
    assertEquals(customMessage, error2.getMessage());
    
    // Verify it extends Error
    assertTrue(error1 instanceof Error);
  }

  /**
   * Test FlashProgrammingError exception.
   */
  @Test
  public void testFlashProgrammingError() {
    // Test default constructor
    FlashProgrammingError error1 = new FlashProgrammingError();
    assertNotNull(error1);
    assertNotNull(error1.getMessage());
    assertTrue(error1.getMessage().contains("programming"));
    
    // Test constructor with custom message
    String customMessage = "Custom programming error";
    FlashProgrammingError error2 = new FlashProgrammingError(customMessage);
    assertNotNull(error2);
    assertEquals(customMessage, error2.getMessage());
    
    // Verify it extends Error
    assertTrue(error1 instanceof Error);
  }

  /**
   * Test FlashTimeoutError exception.
   */
  @Test
  public void testFlashTimeoutError() {
    // Test default constructor
    FlashTimeoutError error1 = new FlashTimeoutError();
    assertNotNull(error1);
    assertNotNull(error1.getMessage());
    assertTrue(error1.getMessage().contains("timeout"));
    
    // Test constructor with custom message
    String customMessage = "Custom timeout error";
    FlashTimeoutError error2 = new FlashTimeoutError(customMessage);
    assertNotNull(error2);
    assertEquals(customMessage, error2.getMessage());
    
    // Verify it extends Error
    assertTrue(error1 instanceof Error);
  }

  /**
   * Test that all flash error exceptions can be caught as Error.
   */
  @Test
  public void testErrorHierarchy() {
    Exception[] errors = new Exception[] {
        new FlashWriteProtectionError(),
        new FlashSequenceError(),
        new FlashProgrammingError(),
        new FlashTimeoutError()
    };
    
    for (Exception error : errors) {
      assertTrue("All flash errors should extend Error", error instanceof Error);
    }
  }
}
