/**
 * Copyright (C) 2015-2019 Philip Helger (www.helger.com)
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
package com.helger.as4.client;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.w3c.dom.Document;

import com.helger.as4.error.IEbmsError;
import com.helger.as4.http.HttpXMLEntity;
import com.helger.as4.messaging.domain.AS4ErrorMessage;
import com.helger.as4.messaging.domain.MessageHelperMethods;
import com.helger.as4lib.ebms3header.Ebms3Error;
import com.helger.as4lib.ebms3header.Ebms3MessageInfo;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;

/**
 * AS4 client for {@link AS4ErrorMessage} objects.
 *
 * @author Philip Helger
 */
public class AS4ClientErrorMessage extends AbstractAS4ClientSignalMessage
{
  private final ICommonsList <Ebms3Error> m_aErrorMessages = new CommonsArrayList <> ();

  public AS4ClientErrorMessage ()
  {}

  public final void addErrorMessage (@Nonnull final IEbmsError aError, @Nonnull final Locale aLocale)
  {
    ValueEnforcer.notNull (aError, "Error");
    ValueEnforcer.notNull (aLocale, "Locale");

    m_aErrorMessages.add (aError.getAsEbms3Error (aLocale, getRefToMessageID ()));
  }

  @Nonnull
  @ReturnsMutableObject
  public final ICommonsList <Ebms3Error> errorMessages ()
  {
    return m_aErrorMessages;
  }

  private void _checkMandatoryAttributes ()
  {
    if (getSOAPVersion () == null)
      throw new IllegalStateException ("A SOAPVersion must be set.");

    if (m_aErrorMessages.isEmpty ())
      throw new IllegalStateException ("No Errors specified!");
    if (m_aErrorMessages.containsAny (Objects::isNull))
      throw new IllegalStateException ("Errors may not contain null elements.");

    if (!hasRefToMessageID ())
      throw new IllegalStateException ("No reference to a message set.");
  }

  @Override
  public AS4ClientBuiltMessage buildMessage (@Nullable final IAS4ClientBuildMessageCallback aCallback) throws Exception
  {
    _checkMandatoryAttributes ();

    // Create a new message ID for each build!
    final String sMessageID = createMessageID ();

    final Ebms3MessageInfo aEbms3MessageInfo = MessageHelperMethods.createEbms3MessageInfo (sMessageID,
                                                                                            getRefToMessageID ());

    final AS4ErrorMessage aErrorMsg = AS4ErrorMessage.create (getSOAPVersion (), aEbms3MessageInfo, m_aErrorMessages);

    if (aCallback != null)
      aCallback.onAS4Message (aErrorMsg);

    final Document aDoc = aErrorMsg.getAsSOAPDocument ();

    if (aCallback != null)
      aCallback.onSOAPDocument (aDoc);

    // Wrap SOAP XML
    return new AS4ClientBuiltMessage (sMessageID, new HttpXMLEntity (aDoc, getSOAPVersion ()));
  }
}
