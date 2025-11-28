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

/**
 * Interface for ST-Link USB communication.
 * This abstracts the USB layer to allow different USB backends.
 */
public interface StLinkInterface {
  
  /**
   * Open the ST-Link device.
   */
  void open() throws Error;
  
  /**
   * Close the ST-Link device.
   */
  void close();
  
  /**
   * Send a command to the ST-Link and receive a response.
   * 
   * @param command Command bytes to send
   * @param responseLength Expected response length
   * @return Response bytes
   */
  byte[] transfer(byte[] command, int responseLength) throws Error, TimeoutException;
  
  /**
   * Send a command and then write data.
   * 
   * @param command Command bytes
   * @param data Data to write
   */
  void transferOut(byte[] command, byte[] data) throws Error, TimeoutException;
  
  /**
   * Send a command and then read data.
   * 
   * @param command Command bytes
   * @param length Number of bytes to read
   * @return Data read
   */
  byte[] transferIn(byte[] command, int length) throws Error, TimeoutException;
  
  /**
   * Get the serial number of the device.
   */
  String getSerialNumber();
}
