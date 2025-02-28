/*
 * Copyright (C) 2015-2023 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.peppol.supplementary.tools;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.io.file.SimpleFileIO;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.phase4.dump.AS4DumpReader;
import com.helger.phase4.dump.AS4DumpReader.IDecryptedPayloadConsumer;

/**
 * This is a small tool that demonstrates how the "as4in" files can be decrypted
 * later, assuming the correct certificate is provided.
 *
 * @author Philip Helger
 */
public final class MainDecipherAS4In
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MainDecipherAS4In.class);

  public static void main (final String [] args) throws Exception
  {
    // The file to decipher. Should be MIME based message.
    final File aFile = new File ("src/test/resources/incoming/165445-9-8a813f8d-3dda-4ef9-868e-f6d829972d4e.as4in");
    if (!aFile.exists ())
      throw new IllegalStateException ("The file " + aFile.getAbsolutePath () + " does not exist");

    LOGGER.info ("Reading " + aFile.getName ());
    final byte [] aBytes = SimpleFileIO.getAllFileBytes (aFile);

    final IAS4CryptoFactory aCryptoFactory = AS4CryptoFactoryProperties.getDefaultInstance ();
    // What to do with the decrypted payload
    final IDecryptedPayloadConsumer aDecryptedConsumer = (idx,
                                                          aDecryptedBytes) -> SimpleFileIO.writeFile (new File (aFile.getParentFile (),
                                                                                                                "payload-" +
                                                                                                                                        idx +
                                                                                                                                        ".decrypted"),
                                                                                                      aDecryptedBytes);

    // Do it
    AS4DumpReader.decryptAS4In (aBytes, aCryptoFactory, null, aDecryptedConsumer);
  }
}
