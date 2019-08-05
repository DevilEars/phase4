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

import javax.annotation.Nonnull;

import org.apache.http.HttpEntity;

import com.helger.as4.http.HttpMimeMessageEntity;
import com.helger.as4.http.HttpXMLEntity;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.string.ToStringGenerator;

public final class AS4ClientBuiltMessage
{
  private final String m_sMessageID;
  private final HttpEntity m_aHttpEntity;

  public AS4ClientBuiltMessage (@Nonnull @Nonempty final String sMessageID, @Nonnull final HttpXMLEntity aHttpEntity)
  {
    m_sMessageID = ValueEnforcer.notEmpty (sMessageID, "MessageID");
    m_aHttpEntity = ValueEnforcer.notNull (aHttpEntity, "HttpEntity");
  }

  public AS4ClientBuiltMessage (@Nonnull @Nonempty final String sMessageID,
                                @Nonnull final HttpMimeMessageEntity aHttpEntity)
  {
    m_sMessageID = ValueEnforcer.notEmpty (sMessageID, "MessageID");
    m_aHttpEntity = ValueEnforcer.notNull (aHttpEntity, "HttpEntity");
  }

  @Nonnull
  @Nonempty
  public String getMessageID ()
  {
    return m_sMessageID;
  }

  @Nonnull
  public HttpEntity getHttpEntity ()
  {
    return m_aHttpEntity;
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("MessageID", m_sMessageID)
                                       .append ("HttpEntity", m_aHttpEntity)
                                       .getToString ();
  }
}
