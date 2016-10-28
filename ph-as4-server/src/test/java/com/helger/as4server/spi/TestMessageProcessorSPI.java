/**
 * Copyright (C) 2015-2016 Philip Helger (www.helger.com)
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
package com.helger.as4server.spi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.as4server.attachment.IIncomingAttachment;
import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.state.ESuccess;

/**
 * Test implementation of {@link IAS4ServletMessageProcessorSPI}
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class TestMessageProcessorSPI implements IAS4ServletMessageProcessorSPI
{
  @Nonnull
  public AS4MessageProcessorResult processAS4Message (@Nullable final byte [] aPayload,
                                                        @Nullable final ICommonsList <IIncomingAttachment> aIncomingAttachments)
  {
    System.out.println ("HERE!Q!!!!!!");
    return new AS4MessageProcessorResult (ESuccess.SUCCESS);
  }
}
