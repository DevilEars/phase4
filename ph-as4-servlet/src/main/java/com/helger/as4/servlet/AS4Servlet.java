/**
 * Copyright (C) 2015-2017 Philip Helger (www.helger.com)
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
package com.helger.as4.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.as4.CAS4;
import com.helger.as4.attachment.EAS4CompressionMode;
import com.helger.as4.attachment.IIncomingAttachmentFactory;
import com.helger.as4.attachment.WSS4JAttachment;
import com.helger.as4.attachment.WSS4JAttachment.IHasAttachmentSourceStream;
import com.helger.as4.crypto.ECryptoAlgorithmCrypt;
import com.helger.as4.crypto.ECryptoAlgorithmSign;
import com.helger.as4.crypto.ECryptoAlgorithmSignDigest;
import com.helger.as4.error.EEbmsError;
import com.helger.as4.error.EEbmsErrorSeverity;
import com.helger.as4.messaging.domain.AS4ErrorMessage;
import com.helger.as4.messaging.domain.AS4ReceiptMessage;
import com.helger.as4.messaging.domain.AS4UserMessage;
import com.helger.as4.messaging.domain.CreateErrorMessage;
import com.helger.as4.messaging.domain.CreateReceiptMessage;
import com.helger.as4.messaging.domain.CreateUserMessage;
import com.helger.as4.messaging.domain.EAS4MessageType;
import com.helger.as4.messaging.domain.MessageHelperMethods;
import com.helger.as4.messaging.encrypt.EncryptionCreator;
import com.helger.as4.messaging.mime.MimeMessageCreator;
import com.helger.as4.messaging.sign.SignedMessageCreator;
import com.helger.as4.mgr.MetaAS4Manager;
import com.helger.as4.model.MEPHelper;
import com.helger.as4.model.pmode.EPModeSendReceiptReplyPattern;
import com.helger.as4.model.pmode.IPMode;
import com.helger.as4.model.pmode.PMode;
import com.helger.as4.model.pmode.PModeManager;
import com.helger.as4.model.pmode.PModeParty;
import com.helger.as4.model.pmode.config.IPModeConfig;
import com.helger.as4.model.pmode.config.PModeConfigManager;
import com.helger.as4.model.pmode.leg.PModeLeg;
import com.helger.as4.model.pmode.leg.PModeLegBusinessInformation;
import com.helger.as4.partner.Partner;
import com.helger.as4.partner.PartnerManager;
import com.helger.as4.profile.IAS4Profile;
import com.helger.as4.servlet.debug.AS4DebugInputStream;
import com.helger.as4.servlet.debug.IAS4DebugIncomingCallback;
import com.helger.as4.servlet.mgr.AS4ServerConfiguration;
import com.helger.as4.servlet.mgr.AS4ServerSettings;
import com.helger.as4.servlet.mgr.AS4ServletMessageProcessorManager;
import com.helger.as4.servlet.soap.AS4SingleSOAPHeader;
import com.helger.as4.servlet.soap.ISOAPHeaderElementProcessor;
import com.helger.as4.servlet.soap.SOAPHeaderElementProcessorRegistry;
import com.helger.as4.servlet.spi.AS4MessageProcessorResult;
import com.helger.as4.servlet.spi.IAS4ServletMessageProcessorSPI;
import com.helger.as4.soap.ESOAPVersion;
import com.helger.as4.util.AS4ResourceManager;
import com.helger.as4.util.AS4XMLHelper;
import com.helger.as4.util.StringMap;
import com.helger.as4lib.ebms3header.Ebms3CollaborationInfo;
import com.helger.as4lib.ebms3header.Ebms3Description;
import com.helger.as4lib.ebms3header.Ebms3Error;
import com.helger.as4lib.ebms3header.Ebms3From;
import com.helger.as4lib.ebms3header.Ebms3MessageInfo;
import com.helger.as4lib.ebms3header.Ebms3MessageProperties;
import com.helger.as4lib.ebms3header.Ebms3PartInfo;
import com.helger.as4lib.ebms3header.Ebms3PartyId;
import com.helger.as4lib.ebms3header.Ebms3PartyInfo;
import com.helger.as4lib.ebms3header.Ebms3PayloadInfo;
import com.helger.as4lib.ebms3header.Ebms3Property;
import com.helger.as4lib.ebms3header.Ebms3To;
import com.helger.as4lib.ebms3header.Ebms3UserMessage;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.collection.ArrayHelper;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.ext.CommonsArrayList;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.equals.EqualsHelper;
import com.helger.commons.error.IError;
import com.helger.commons.error.list.ErrorList;
import com.helger.commons.mime.EMimeContentType;
import com.helger.commons.mime.IMimeType;
import com.helger.commons.mime.MimeType;
import com.helger.commons.mime.MimeTypeParser;
import com.helger.commons.string.StringHelper;
import com.helger.http.EHTTPMethod;
import com.helger.http.EHTTPVersion;
import com.helger.http.HTTPHeaderMap;
import com.helger.http.HTTPStringHelper;
import com.helger.photon.core.servlet.AbstractUnifiedResponseServlet;
import com.helger.photon.security.CSecurity;
import com.helger.photon.security.login.ELoginResult;
import com.helger.photon.security.login.LoggedInUserManager;
import com.helger.security.certificate.CertificateHelper;
import com.helger.servlet.request.RequestHelper;
import com.helger.servlet.response.UnifiedResponse;
import com.helger.web.multipart.MultipartProgressNotifier;
import com.helger.web.multipart.MultipartStream;
import com.helger.web.multipart.MultipartStream.MultipartItemInputStream;
import com.helger.web.scope.IRequestWebScopeWithoutResponse;
import com.helger.xml.ChildElementIterator;
import com.helger.xml.XMLHelper;
import com.helger.xml.serialize.read.DOMReader;

/**
 * AS4 receiving servlet.<br>
 * Use a configuration like the following in your <code>WEB-INF/web.xm</code>
 * file:
 *
 * <pre>
&lt;servlet&gt;
  &lt;servlet-name&gt;AS4Servlet&lt;/servlet-name&gt;
  &lt;servlet-class&gt;com.helger.as4.servlet.AS4Servlet&lt;/servlet-class&gt;
&lt;/servlet&gt;
&lt;servlet-mapping&gt;
  &lt;servlet-name&gt;AS4Servlet&lt;/servlet-name&gt;
  &lt;url-pattern&gt;/as4&lt;/url-pattern&gt;
&lt;/servlet-mapping&gt;
 * </pre>
 *
 * @author Martin Bayerl
 * @author Philip Helger
 */
public final class AS4Servlet extends AbstractUnifiedResponseServlet
{
  private static final Logger s_aLogger = LoggerFactory.getLogger (AS4Servlet.class);
  private static final IMimeType MT_MULTIPART_RELATED = EMimeContentType.MULTIPART.buildMimeType ("related");
  private static IAS4DebugIncomingCallback s_aDebugIncomingCB;

  public AS4Servlet ()
  {}

  @Nullable
  public static IAS4DebugIncomingCallback getDebugIncomingCallback ()
  {
    return s_aDebugIncomingCB;
  }

  public static void setDebugIncomingCallback (@Nullable final IAS4DebugIncomingCallback aCB)
  {
    s_aDebugIncomingCB = aCB;
  }

  @Override
  protected Set <EHTTPMethod> getAllowedHTTPMethods ()
  {
    return ALLOWED_METHDOS_POST;
  }

  private static void _extractAllHeaders (@Nonnull final ESOAPVersion eSOAPVersion,
                                          @Nonnull final Node aHeaderNode,
                                          @Nonnull final ICommonsList <AS4SingleSOAPHeader> aHeaders)
  {
    for (final Element aHeaderChild : new ChildElementIterator (aHeaderNode))
    {
      final QName aQName = XMLHelper.getQName (aHeaderChild);
      final String sMustUnderstand = aHeaderChild.getAttributeNS (eSOAPVersion.getNamespaceURI (), "mustUnderstand");
      final boolean bIsMustUnderstand = eSOAPVersion.getMustUnderstandValue (true).equals (sMustUnderstand);
      aHeaders.add (new AS4SingleSOAPHeader (aHeaderChild, aQName, bIsMustUnderstand));
    }
  }

  private static void _decompressAttachments (@Nonnull final Ebms3UserMessage aUserMessage,
                                              @Nonnull final AS4MessageState aState,
                                              @Nonnull final ICommonsList <WSS4JAttachment> aDecryptedAttachments)
  {
    for (final WSS4JAttachment aIncomingAttachment : aDecryptedAttachments.getClone ())
    {
      final EAS4CompressionMode eCompressionMode = aState.getAttachmentCompressionMode (aIncomingAttachment.getId ());
      if (eCompressionMode != null)
      {
        final IHasAttachmentSourceStream aOldISP = aIncomingAttachment.getInputStreamProvider ();
        aIncomingAttachment.setSourceStreamProvider ( () -> eCompressionMode.getDecompressStream (aOldISP.getInputStream ()));

        final String sAttachmentContentID = StringHelper.trimStart (aIncomingAttachment.getId (), "attachment=");
        final Ebms3PartInfo aPart = CollectionHelper.findFirst (aUserMessage.getPayloadInfo ().getPartInfo (),
                                                                x -> x.getHref ().contains (sAttachmentContentID));
        if (aPart != null)
        {
          final Ebms3Property aProperty = CollectionHelper.findFirst (aPart.getPartProperties ().getProperty (),
                                                                      x -> x.getName ()
                                                                            .equals (CreateUserMessage.PART_PROPERTY_MIME_TYPE));
          if (aProperty != null)
          {
            aIncomingAttachment.overwriteMimeType (aProperty.getValue ());
          }
        }
      }
    }
  }

  private void _handleSOAPMessage (@Nonnull final AS4ResourceManager aResMgr,
                                   @Nonnull final Document aSOAPDocument,
                                   @Nonnull final ESOAPVersion eSOAPVersion,
                                   @Nonnull final ICommonsList <WSS4JAttachment> aIncomingAttachments,
                                   @Nonnull final AS4Response aAS4Response,
                                   @Nonnull final Locale aLocale) throws Exception
  {
    ValueEnforcer.notNull (aSOAPDocument, "SOAPDocument");
    ValueEnforcer.notNull (eSOAPVersion, "SOAPVersion");
    ValueEnforcer.notNull (aIncomingAttachments, "IncomingAttachments");
    ValueEnforcer.notNull (aAS4Response, "AS4Response");
    ValueEnforcer.notNull (aLocale, "Locale");

    if (s_aLogger.isDebugEnabled ())
    {
      s_aLogger.debug ("Received the following SOAP " + eSOAPVersion.getVersion () + " document:");
      s_aLogger.debug (AS4XMLHelper.serializeXML (aSOAPDocument));
      s_aLogger.debug ("Including the following attachments:");
      s_aLogger.debug (aIncomingAttachments.toString ());
    }

    // Find SOAP header
    final Node aHeaderNode = XMLHelper.getFirstChildElementOfName (aSOAPDocument.getDocumentElement (),
                                                                   eSOAPVersion.getNamespaceURI (),
                                                                   eSOAPVersion.getHeaderElementName ());
    if (aHeaderNode == null)
    {
      aAS4Response.setBadRequest ("SOAP document is missing a Header element");
      return;
    }

    // Find SOAP body
    Node aBodyNode = XMLHelper.getFirstChildElementOfName (aSOAPDocument.getDocumentElement (),
                                                           eSOAPVersion.getNamespaceURI (),
                                                           eSOAPVersion.getBodyElementName ());
    if (aBodyNode == null)
    {
      aAS4Response.setBadRequest ("SOAP document is missing a Body element");
      return;
    }

    // Extract all header elements including their mustUnderstand value
    final ICommonsList <AS4SingleSOAPHeader> aHeaders = new CommonsArrayList <> ();
    _extractAllHeaders (eSOAPVersion, aHeaderNode, aHeaders);

    final ICommonsList <Ebms3Error> aErrorMessages = new CommonsArrayList <> ();

    // This is where all data from the SOAP headers is stored to
    final AS4MessageState aState = new AS4MessageState (eSOAPVersion, aResMgr);

    // handle all headers in the order of the registered handlers!
    for (final Map.Entry <QName, ISOAPHeaderElementProcessor> aEntry : SOAPHeaderElementProcessorRegistry.getInstance ()
                                                                                                         .getAllElementProcessors ()
                                                                                                         .entrySet ())
    {
      final QName aQName = aEntry.getKey ();

      // Check if this message contains a header for the current handler
      final AS4SingleSOAPHeader aHeader = aHeaders.findFirst (x -> aQName.equals (x.getQName ()));
      if (aHeader == null)
      {
        // no header element for current processor
        if (s_aLogger.isDebugEnabled ())
          s_aLogger.debug ("Message contains no SOAP header element with QName " + aQName.toString ());
        continue;
      }

      final ISOAPHeaderElementProcessor aProcessor = aEntry.getValue ();
      if (s_aLogger.isDebugEnabled ())
        s_aLogger.debug ("Processing SOAP header element " + aQName.toString () + " with processor " + aProcessor);

      // Process element
      final ErrorList aErrorList = new ErrorList ();
      if (aProcessor.processHeaderElement (aSOAPDocument,
                                           aHeader.getNode (),
                                           aIncomingAttachments,
                                           aState,
                                           aErrorList,
                                           aLocale)
                    .isSuccess ())
      {
        // Mark header as processed (for mustUnderstand check)
        aHeader.setProcessed (true);
      }
      else
      {
        // upon failure, the element stays unprocessed and sends back a signal
        // message with the errors
        s_aLogger.warn ("Failed to process SOAP header element " +
                        aQName.toString () +
                        " with processor " +
                        aProcessor +
                        "; error details: " +
                        aErrorList);

        for (final IError aError : aErrorList)
        {
          final EEbmsError ePredefinedError = EEbmsError.getFromErrorCodeOrNull (aError.getErrorID ());
          if (ePredefinedError != null)
            aErrorMessages.add (ePredefinedError.getAsEbms3Error (aLocale));
          else
          {
            final Ebms3Error aEbms3Error = new Ebms3Error ();
            aEbms3Error.setErrorDetail (aError.getErrorText (aLocale));
            aEbms3Error.setErrorCode (aError.getErrorID ());
            aEbms3Error.setSeverity (aError.getErrorLevel ().getID ());
            aEbms3Error.setOrigin (aError.getErrorFieldName ());
            aErrorMessages.add (aEbms3Error);
          }
        }

        // Stop processing of other headers
        break;
      }
    }

    // Now check if all must understand headers were processed
    Ebms3UserMessage aUserMessage = null;
    Node aPayloadNode = null;
    ICommonsList <WSS4JAttachment> aDecryptedAttachments = null;

    if (aErrorMessages.isEmpty ())
    {
      // Are all must-understand headers processed?
      for (final AS4SingleSOAPHeader aHeader : aHeaders)
        if (aHeader.isMustUnderstand () && !aHeader.isProcessed ())
        {
          aAS4Response.setBadRequest ("Error processing required SOAP header element " +
                                      aHeader.getQName ().toString ());
          return;
        }

      // Every message can only contain 1 Usermessage but 0..n signalmessages
      aUserMessage = aState.getMessaging ().getUserMessageAtIndex (0);

      // Ensure the decrypted attachments are used
      aDecryptedAttachments = aState.hasDecryptedAttachments () ? aState.getDecryptedAttachments ()
                                                                : aState.getOriginalAttachments ();

      // Decompress attachments (if compressed)
      _decompressAttachments (aUserMessage, aState, aDecryptedAttachments);

      final Document aDecryptedSOAPDoc = aState.getDecryptedSOAPDocument ();

      if (aDecryptedSOAPDoc != null)
      {
        // Re-evaluate body node from decrypted SOAP document
        aBodyNode = XMLHelper.getFirstChildElementOfName (aDecryptedSOAPDoc.getDocumentElement (),
                                                          eSOAPVersion.getNamespaceURI (),
                                                          eSOAPVersion.getBodyElementName ());
        if (aBodyNode == null)
        {
          aAS4Response.setBadRequest ("Decrypted SOAP document is missing a Body element");
          return;
        }
      }
      aPayloadNode = aBodyNode.getFirstChild ();
    }

    if (aErrorMessages.isEmpty ())
    {
      // Check if originalSender and finalRecipient are present
      // Since these two properties are mandatory
      if (aUserMessage.getMessageProperties () != null)
      {
        final List <Ebms3Property> aProps = aUserMessage.getMessageProperties ().getProperty ();
        if (!aProps.isEmpty ())
        {
          final String sErrorText = _checkPropertiesOrignalSenderAndFinalRecipient (aProps);
          if (StringHelper.hasText (sErrorText))
          {
            aAS4Response.setBadRequest (sErrorText);
            return;
          }
        }
        else
        {
          aAS4Response.setBadRequest ("Message Property element present but no properties");
          return;
        }
      }
      else
      {
        aAS4Response.setBadRequest ("No Message Properties present but OriginalSender and finalRecipient have to be present");
        return;
      }

      // Additional Matrix on what should happen in certain scenarios
      // Check if Partner+Partner combination is already present
      // P+P neu + PConfig da = anlegen
      // P+P neu + PConfig neu = Fehler
      // P+P neu + PConfig Id fehlt = default
      // P+P da + PConfig neu = fehler
      // P+P da + PConfig da = nix tun
      // P+P da + PConfig id fehlt = default

      final String sConfigID = aState.getPModeConfig ().getID ();

      final PModeConfigManager aPModeConfigMgr = MetaAS4Manager.getPModeConfigMgr ();
      final PartnerManager aPartnerMgr = MetaAS4Manager.getPartnerMgr ();

      if (aPModeConfigMgr.containsWithID (sConfigID))
      {
        if (aPartnerMgr.containsWithID (aState.getInitiatorID ()) &&
            aPartnerMgr.containsWithID (aState.getResponderID ()))
        {
          _ensurePModeIsPresent (aState, sConfigID, aUserMessage.getPartyInfo ());
        }
        else
        {
          if (!aPartnerMgr.containsWithID (aState.getInitiatorID ()))
          {
            _createOrUpdatePartner (aState.getUsedCertificate (), aState.getInitiatorID ());
          }
          else
            if (!aPartnerMgr.containsWithID (aState.getResponderID ()))
            {
              s_aLogger.warn ("Responder is not the default or an already registered one");
              _createOrUpdatePartner (null, aState.getResponderID ());
            }

          _ensurePModeIsPresent (aState, sConfigID, aUserMessage.getPartyInfo ());
        }
      }
    }

    // Storing for two-way response messages
    final ICommonsList <WSS4JAttachment> aResponseAttachments = new CommonsArrayList <> ();

    if (aErrorMessages.isEmpty () && _isNotPingMessage (aState.getPModeConfig ()))
    {
      final String sMessageID = aUserMessage.getMessageInfo ().getMessageId ();
      final boolean bIsDuplicate = MetaAS4Manager.getIncomingDuplicateMgr ().registerAndCheck (sMessageID).isBreak ();
      if (bIsDuplicate)
      {
        s_aLogger.info ("Not invoking SPIs, because message was already handled!");
        final Ebms3Description aDesc = new Ebms3Description ();
        aDesc.setLang (aLocale.getLanguage ());
        aDesc.setValue ("Another message with the same ID was already received!");
        aErrorMessages.add (EEbmsError.EBMS_OTHER.getAsEbms3Error (aLocale, sMessageID, "", aDesc));
      }
      else
      {
        // Invoke all SPIs
        for (final IAS4ServletMessageProcessorSPI aProcessor : AS4ServletMessageProcessorManager.getAllProcessors ())
          try
          {
            if (s_aLogger.isDebugEnabled ())
              s_aLogger.debug ("Invoking AS4 message processor " + aProcessor);

            final AS4MessageProcessorResult aResult = aProcessor.processAS4Message (aUserMessage,
                                                                                    aPayloadNode,
                                                                                    aDecryptedAttachments);
            if (aResult == null)
              throw new IllegalStateException ("No result object present!");

            if (aResult.isSuccess ())
            {
              // Add response attachments, payloads
              aResult.addAllAttachmentsTo (aResponseAttachments);
              if (s_aLogger.isDebugEnabled ())
                s_aLogger.debug ("Successfully invoked AS4 message processor " + aProcessor);
            }
            else
            {
              s_aLogger.warn ("Invoked AS4 message processor SPI " + aProcessor + " returned a failure");

              final Ebms3Error aError = new Ebms3Error ();
              aError.setSeverity (EEbmsErrorSeverity.FAILURE.getSeverity ());
              aError.setErrorCode (EEbmsError.EBMS_OTHER.getErrorCode ());
              aError.setRefToMessageInError (sMessageID);
              final Ebms3Description aDesc = new Ebms3Description ();
              aDesc.setValue (aResult.getErrorMessage ());
              aDesc.setLang (aLocale.getLanguage ());
              aError.setDescription (aDesc);
              aErrorMessages.add (aError);

              // Stop processing
              break;
            }
          }
          catch (final Throwable t)
          {
            s_aLogger.error ("Error processing incoming AS4 message with processor " + aProcessor, t);
            aAS4Response.setBadRequest ("Error processing incoming AS4 message with processor " +
                                        aProcessor +
                                        ", Exception: " +
                                        t.getLocalizedMessage ());
            return;
          }
      }
    }

    final IPModeConfig aPModeConfig = aState.getPModeConfig ();
    if (aErrorMessages.isEmpty ())
    {
      // PModeConfig - determined inside SPI providers!
      if (aPModeConfig == null)
      {
        aAS4Response.setBadRequest ("No AS4 P-Mode configuration found!");
        return;
      }

      {
        // Only do profile checks if a profile is set
        final String sProfileName = AS4ServerConfiguration.getAS4ProfileName ();
        if (StringHelper.hasText (sProfileName))
        {
          final IAS4Profile aProfile = MetaAS4Manager.getProfileMgr ().getProfileOfID (sProfileName);
          if (aProfile == null)
          {
            aAS4Response.setBadRequest ("The AS4 profile " + sProfileName + " does not exist.");
            return;
          }

          // Profile Checks gets set when started with Server
          final ErrorList aErrorList = new ErrorList ();
          aProfile.getValidator ().validatePModeConfig (aPModeConfig, aErrorList);
          aProfile.getValidator ().validateUserMessage (aUserMessage, aErrorList);
          if (aErrorList.isNotEmpty ())
          {
            s_aLogger.error ("Error validating incoming AS4 message with the profile " + aProfile.getDisplayName ());
            aAS4Response.setBadRequest ("Error validating incoming AS4 message with the profile " +
                                        aProfile.getDisplayName () +
                                        "\n Following errors are present: " +
                                        aErrorList.getAllErrors ().getAllTexts (aLocale));
            return;
          }
        }
      }
    }

    // Generate ErrorMessage if errors in the process are present and the
    // partners declared in their pmode config they want an error response
    if (aErrorMessages.isNotEmpty ())
    {
      if (_isSendErrorAsResponse (aPModeConfig))
      {
        final AS4ErrorMessage aErrorMsg = CreateErrorMessage.createErrorMessage (eSOAPVersion,
                                                                                 MessageHelperMethods.createEbms3MessageInfo (),
                                                                                 aErrorMessages);

        aAS4Response.setContentAndCharset (AS4XMLHelper.serializeXML (aErrorMsg.getAsSOAPDocument ()),
                                           StandardCharsets.UTF_8)
                    .setMimeType (eSOAPVersion.getMimeType ());
      }
      else
        s_aLogger.warn ("Not sending back the error, because sending error response is prohibited in PMode");
    }
    else
    {
      // If no Error is present check if partners declared if they want a
      // response and if this response should contain non-repudiation
      // information if applicable
      if (_isSendReceiptAsResponse (aPModeConfig))
      {
        if (aPModeConfig.getMEP ().isOneWay ())
        {
          final Ebms3MessageInfo aEbms3MessageInfo = MessageHelperMethods.createEbms3MessageInfo ();
          final AS4ReceiptMessage aReceiptMessage = CreateReceiptMessage.createReceiptMessage (eSOAPVersion,
                                                                                               aEbms3MessageInfo,
                                                                                               aUserMessage,
                                                                                               aSOAPDocument,
                                                                                               _isSendNonRepudiationInformation (aPModeConfig))
                                                                        .setMustUnderstand (true);

          // We've got our response
          final Document aResponseDoc = aReceiptMessage.getAsSOAPDocument ();
          aAS4Response.setContentAndCharset (AS4XMLHelper.serializeXML (aResponseDoc), StandardCharsets.UTF_8)
                      .setMimeType (eSOAPVersion.getMimeType ());
        }
        else
        {
          // TODO twowaypushpush
          // TWO - WAY
          // Except for two way push push, every response as usermessage is
          // always on leg 2
          final PModeLeg aLeg2 = aPModeConfig.getLeg2 ();
          if (aLeg2 != null)
          {
            if (MEPHelper.isValidResponseType (aPModeConfig.getMEP (),
                                               aPModeConfig.getMEPBinding (),
                                               EAS4MessageType.USER_MESSAGE,
                                               false))
            {

              final Ebms3MessageInfo aEbms3MessageInfo = MessageHelperMethods.createEbms3MessageInfo (MessageHelperMethods.createRandomMessageID (),
                                                                                                      aUserMessage.getMessageInfo ()
                                                                                                                  .getMessageId ());
              final Ebms3PayloadInfo aEbms3PayloadInfo = CreateUserMessage.createEbms3PayloadInfo (null,
                                                                                                   aResponseAttachments);

              // Invert from and to role from original user message
              final Ebms3PartyInfo aEbms3PartyInfo = CreateUserMessage.createEbms3PartyInfo (aUserMessage.getPartyInfo ()
                                                                                                         .getTo ()
                                                                                                         .getRole (),
                                                                                             aUserMessage.getPartyInfo ()
                                                                                                         .getTo ()
                                                                                                         .getPartyIdAtIndex (0)
                                                                                                         .getValue (),
                                                                                             aUserMessage.getPartyInfo ()
                                                                                                         .getFrom ()
                                                                                                         .getRole (),
                                                                                             aUserMessage.getPartyInfo ()
                                                                                                         .getFrom ()
                                                                                                         .getPartyIdAtIndex (0)
                                                                                                         .getValue ());

              // Should be exactly the same as incoming message
              final Ebms3CollaborationInfo aEbms3CollaborationInfo = aUserMessage.getCollaborationInfo ();

              // Need to switch C1 and C4 around from the original usermessage
              final Ebms3MessageProperties aEbms3MessageProperties = new Ebms3MessageProperties ();
              Ebms3Property aFinalRecipient = null;
              Ebms3Property aOriginalSender = null;
              for (final Ebms3Property aProp : aUserMessage.getMessageProperties ().getProperty ())
              {
                if (aProp.getName ().equals (CAS4.FINAL_RECIPIENT))
                {
                  aOriginalSender = aProp;
                }
                else
                  if (aProp.getName ().equals (CAS4.ORIGINAL_SENDER))
                  {
                    aFinalRecipient = aProp;
                  }
              }

              aFinalRecipient.setName (CAS4.ORIGINAL_SENDER);
              aOriginalSender.setName (CAS4.FINAL_RECIPIENT);

              aEbms3MessageProperties.addProperty (aFinalRecipient);
              aEbms3MessageProperties.addProperty (aOriginalSender);

              final AS4UserMessage aResponeUserMesage = CreateUserMessage.createUserMessage (aEbms3MessageInfo,
                                                                                             aEbms3PayloadInfo,
                                                                                             aEbms3CollaborationInfo,
                                                                                             aEbms3PartyInfo,
                                                                                             aEbms3MessageProperties,
                                                                                             eSOAPVersion);
              // We've got our response
              Document aResponseDoc = aResponeUserMesage.getAsSOAPDocument ();

              if (aLeg2.getSecurity () != null)
              {
                if (ECryptoAlgorithmSign.getFromIDOrNull (aLeg2.getSecurity ()
                                                               .getX509SignatureAlgorithmID ()) != null &&
                    ECryptoAlgorithmSignDigest.getFromIDOrNull (aLeg2.getSecurity ()
                                                                     .getX509SignatureHashFunctionID ()) != null)
                {
                  final SignedMessageCreator aCreator = new SignedMessageCreator ();
                  aResponseDoc = aCreator.createSignedMessage (aResponseDoc,
                                                               aLeg2.getProtocol ().getSOAPVersion (),
                                                               aResponseAttachments,
                                                               aResMgr,
                                                               true,
                                                               aLeg2.getSecurity ().getX509SignatureAlgorithm (),
                                                               aLeg2.getSecurity ().getX509SignatureHashFunction ());
                }
              }

              if (aResponseAttachments.isNotEmpty ())
              {

                final MimeMessage aMimeMsg = _generateMimeMessageForResponse (aResMgr,
                                                                              aResponseAttachments,
                                                                              aLeg2,
                                                                              aResponseDoc);

                // Move all mime headers to the HTTP request
                final Enumeration <?> aEnum = aMimeMsg.getAllHeaders ();
                while (aEnum.hasMoreElements ())
                {
                  final Header h = (Header) aEnum.nextElement ();
                  // Make a single-line HTTP header value!
                  aAS4Response.addCustomResponseHeader (h.getName (),
                                                        HTTPStringHelper.getUnifiedHTTPHeaderValue (h.getValue ()));

                  // Remove from MIME message!
                  aMimeMsg.removeHeader (h.getName ());
                }

                // send mime with unified response
                aAS4Response.setContent ( () -> {
                  try
                  {
                    return aMimeMsg.getInputStream ();
                  }
                  catch (IOException | MessagingException ex)
                  {
                    throw new IllegalStateException ("Failed to get MIME input stream", ex);
                  }
                }).setMimeType (MT_MULTIPART_RELATED);
              }
              else
              {
                aAS4Response.setContentAndCharset (AS4XMLHelper.serializeXML (aResponseDoc), StandardCharsets.UTF_8)
                            .setMimeType (eSOAPVersion.getMimeType ());
              }
            }
          }
        }
      }
      else
        s_aLogger.info ("Not sending back the receipt response, because sending receipt response is prohibited in PMode");
    }
  }

  /**
   * Returns the MimeMessage with encrypted attachment or without depending on
   * what is configured in the pmodeconfig within Leg2.
   *
   * @param aResMgr
   *        ResourceManager needed for the encryption process, needs to be
   *        passed down
   * @param aResponseAttachments
   *        The Attachments that should be encrypted
   * @param aLeg2
   *        Leg2 to get necessary information, EncryptionAlgorithm, SOAPVersion
   * @param aResponseDoc
   *        the document that conntains the user message
   * @return a MimeMessage to be sent
   * @throws Exception
   */
  @Nonnull
  private MimeMessage _generateMimeMessageForResponse (final AS4ResourceManager aResMgr,
                                                       final ICommonsList <WSS4JAttachment> aResponseAttachments,
                                                       final PModeLeg aLeg2,
                                                       final Document aResponseDoc) throws Exception
  {
    MimeMessage aMimeMsg = null;
    if (aLeg2.getSecurity () != null)
    {
      if (ECryptoAlgorithmCrypt.getFromIDOrNull (aLeg2.getSecurity ().getX509EncryptionAlgorithmID ()) != null)
      {
        final EncryptionCreator aEncryptCreator = new EncryptionCreator ();
        aMimeMsg = aEncryptCreator.encryptMimeMessage (aLeg2.getProtocol ().getSOAPVersion (),
                                                       aResponseDoc,
                                                       true,
                                                       aResponseAttachments,
                                                       aResMgr,
                                                       aLeg2.getSecurity ().getX509EncryptionAlgorithm ());
      }
    }
    else
    {
      aMimeMsg = new MimeMessageCreator (aLeg2.getProtocol ()
                                              .getSOAPVersion ()).generateMimeMessage (aResponseDoc,
                                                                                       aResponseAttachments);
    }
    return aMimeMsg;
  }

  /**
   * EBMS core specification 4.2 details these default values. In eSENS they get
   * used to implement a ping service, we took this over even outside of eSENS.
   * If you use these default values you can try to "ping" the server, the
   * method just checks if the pmode got these exact values set. If true, no SPI
   * processing is done.
   *
   * @param aPModeConfig
   *        to check
   * @return true if the default values to ping are not used else false
   */
  private static boolean _isNotPingMessage (@Nonnull final IPModeConfig aPModeConfig)
  {
    final PModeLegBusinessInformation aBInfo = aPModeConfig.getLeg1 ().getBusinessInfo ();

    if (aBInfo != null &&
        CAS4.DEFAULT_ACTION_URL.equals (aBInfo.getAction ()) &&
        CAS4.DEFAULT_SERVICE_URL.equals (aBInfo.getService ()))
    {
      return false;
    }
    return true;
  }

  /**
   * Checks if in the given PModeConfig the isSendReceiptNonRepudiation is set
   * or not.
   *
   * @param aPModeConfig
   *        to check the attribute
   * @return Returns the value if set, else DEFAULT <code>false</code>.
   */
  private static boolean _isSendNonRepudiationInformation (@Nullable final IPModeConfig aPModeConfig)
  {
    if (aPModeConfig != null)
      if (aPModeConfig.getLeg1 () != null)
        if (aPModeConfig.getLeg1 ().getSecurity () != null)
          if (aPModeConfig.getLeg1 ().getSecurity ().isSendReceiptNonRepudiationDefined ())
            return aPModeConfig.getLeg1 ().getSecurity ().isSendReceiptNonRepudiation ();
    // Default behavior
    return false;
  }

  /**
   * Checks if in the given PModeConfig isReportAsResponse is set.
   *
   * @param aPModeConfig
   *        to check the attribute
   * @return Returns the value if set, else DEFAULT <code>TRUE</code>.
   */
  private static boolean _isSendErrorAsResponse (@Nullable final IPModeConfig aPModeConfig)
  {
    if (aPModeConfig != null)
      if (aPModeConfig.getLeg1 () != null)
        if (aPModeConfig.getLeg1 ().getErrorHandling () != null)
          if (aPModeConfig.getLeg1 ().getErrorHandling ().isReportAsResponseDefined ())
          {
            // Note: this is enabled in Default PMode
            return aPModeConfig.getLeg1 ().getErrorHandling ().isReportAsResponse ();
          }
    // Default behavior
    return true;
  }

  /**
   * Checks if a ReceiptReplyPattern is set to Response or not.
   *
   * @param aPModeConfig
   *        to check the attribute
   * @return Returns the value if set, else DEFAULT <code>TRUE</code>.
   */
  private static boolean _isSendReceiptAsResponse (@Nullable final IPModeConfig aPModeConfig)
  {
    if (aPModeConfig != null)
      if (aPModeConfig.getLeg1 () != null)
        if (aPModeConfig.getLeg1 ().getSecurity () != null)
        {
          // Note: this is enabled in Default PMode
          return EPModeSendReceiptReplyPattern.RESPONSE.equals (aPModeConfig.getLeg1 ()
                                                                            .getSecurity ()
                                                                            .getSendReceiptReplyPattern ());
        }
    // Default behaviour if the value is not set or no security is existing
    return true;
  }

  /**
   * Creates or Updates are Partner. Overwrites with the values in the parameter
   * or creates a new Partner if not present in the PartnerManager already.
   *
   * @param aUsedCertificate
   *        Certificate that should be used
   * @param sID
   *        ID of the Partner
   */
  private static void _createOrUpdatePartner (@Nullable final X509Certificate aUsedCertificate,
                                              @Nonnull final String sID)
  {
    final StringMap aStringMap = new StringMap ();
    aStringMap.setAttribute (Partner.ATTR_PARTNER_NAME, sID);
    if (aUsedCertificate != null)
      aStringMap.setAttribute (Partner.ATTR_CERT, CertificateHelper.getPEMEncodedCertificate (aUsedCertificate));

    final PartnerManager aPartnerMgr = MetaAS4Manager.getPartnerMgr ();
    aPartnerMgr.createOrUpdatePartner (sID, aStringMap);
  }

  /**
   * Checks the mandatory properties OriginalSender and FinalRecipient if those
   * two are set.
   *
   * @param aPropertyList
   *        the property list that should be checked for the two specific ones
   * @return <code>null</code> if both properties are present, else returns the
   *         error message that should be returned to the user.
   */
  @Nullable
  private static String _checkPropertiesOrignalSenderAndFinalRecipient (@Nonnull final List <Ebms3Property> aPropertyList)
  {
    String sOriginalSenderC1 = null;
    String sFinalRecipientC4 = null;

    for (final Ebms3Property sProperty : aPropertyList)
    {
      if (sProperty.getName ().equals (CAS4.ORIGINAL_SENDER))
        sOriginalSenderC1 = sProperty.getValue ();
      else
        if (sProperty.getName ().equals (CAS4.FINAL_RECIPIENT))
          sFinalRecipientC4 = sProperty.getValue ();
    }

    if (StringHelper.hasNoText (sOriginalSenderC1))
    {
      return CAS4.ORIGINAL_SENDER + " property is empty or not existant but mandatory";
    }
    if (StringHelper.hasNoText (sFinalRecipientC4))
    {
      return CAS4.FINAL_RECIPIENT + " property is empty or not existant but mandatory";
    }
    return null;
  }

  /**
   * Creates a PMode if it does not exist already.
   *
   * @param aState
   *        needed to get Responder and Initiator
   * @param sConfigID
   *        needed to get the PModeConfig for the PMode
   * @param aPartyInfo
   *        Party information from user message, needed to get full information
   *        of the Initiator and Responder
   */
  private static void _ensurePModeIsPresent (@Nonnull final AS4MessageState aState,
                                             @Nonnull final String sConfigID,
                                             @Nonnull final Ebms3PartyInfo aPartyInfo)
  {
    final PModeManager aPModeMgr = MetaAS4Manager.getPModeMgr ();
    if (aPModeMgr.containsNone (doesPartnerAndPartnerExist (aState.getInitiatorID (),
                                                            aState.getResponderID (),
                                                            sConfigID)))
    {
      final Ebms3From aFrom = aPartyInfo.getFrom ();
      final Ebms3PartyId aFromID = aFrom.getPartyIdAtIndex (0);
      final Ebms3To aTo = aPartyInfo.getTo ();
      final Ebms3PartyId aToID = aTo.getPartyIdAtIndex (0);
      final IPModeConfig aPModeConfig = MetaAS4Manager.getPModeConfigMgr ().getPModeConfigOfID (sConfigID);
      final PMode aPMode = new PMode (new PModeParty (aFromID.getType (),
                                                      aFromID.getValue (),
                                                      aFrom.getRole (),
                                                      null,
                                                      null),
                                      new PModeParty (aToID.getType (), aToID.getValue (), aTo.getRole (), null, null),
                                      aPModeConfig);
      aPModeMgr.createOrUpdatePMode (aPMode);
    }
    // If the PMode already exists we do not need to do anything
  }

  /**
   * This Predicate helps to find if a Partner (Initiator), Partner (Responder)
   * with a specific PModeConfig already exists.
   *
   * @param sInitiatorID
   *        Initiator to check
   * @param sResponderID
   *        Responder to check
   * @param sPModeConfigID
   *        PModeConfig to check
   * @return aPMode if it already exists with all 3 components
   */
  @Nullable
  public static Predicate <IPMode> doesPartnerAndPartnerExist (@Nullable final String sInitiatorID,
                                                               @Nullable final String sResponderID,
                                                               @Nullable final String sPModeConfigID)
  {
    return x -> EqualsHelper.equals (x.getInitiatorID (), sInitiatorID) &&
                EqualsHelper.equals (x.getResponderID (), sResponderID) &&
                x.getConfigID ().equals (sPModeConfigID);
  }

  @Override
  @Nonnull
  protected AS4Response createUnifiedResponse (@Nonnull final EHTTPVersion eHTTPVersion,
                                               @Nonnull final EHTTPMethod eHTTPMethod,
                                               @Nonnull final HttpServletRequest aHttpRequest)
  {
    return new AS4Response (eHTTPVersion, eHTTPMethod, aHttpRequest);
  }

  @Nonnull
  private InputStream _getRequestIS (@Nonnull final HttpServletRequest aHttpServletRequest) throws IOException
  {
    InputStream aIS = aHttpServletRequest.getInputStream ();
    if (s_aDebugIncomingCB != null)
    {
      // Pass through all headers
      final HTTPHeaderMap aHeaders = RequestHelper.getRequestHeaderMap (aHttpServletRequest);
      s_aDebugIncomingCB.onRequestBegin (aHeaders);

      // Enable incoming debugging
      aIS = new AS4DebugInputStream (aIS, s_aDebugIncomingCB);
    }
    return aIS;
  }

  @Override
  protected void handleRequest (@Nonnull final IRequestWebScopeWithoutResponse aRequestScope,
                                @Nonnull final UnifiedResponse aUnifiedResponse) throws Exception
  {
    final AS4Response aHttpResponse = (AS4Response) aUnifiedResponse;
    final HttpServletRequest aHttpServletRequest = aRequestScope.getRequest ();

    // TODO make locale dynamic
    final Locale aLocale = Locale.US;

    // XXX By default login in admin user; why do we need a logged-in user?
    final ELoginResult e = LoggedInUserManager.getInstance ().loginUser (CSecurity.USER_ADMINISTRATOR_LOGIN,
                                                                         CSecurity.USER_ADMINISTRATOR_PASSWORD);
    assert e.isSuccess () : "Login failed: " + e.toString ();

    try (final AS4ResourceManager aResMgr = new AS4ResourceManager ())
    {
      // Determine content type
      final String sContentType = aHttpServletRequest.getContentType ();
      if (StringHelper.hasNoText (sContentType))
      {
        aHttpResponse.setBadRequest ("Content-Type header is missing");
        return;
      }

      final MimeType aContentType = MimeTypeParser.parseMimeType (sContentType);
      if (s_aLogger.isDebugEnabled ())
        s_aLogger.debug ("Received Content-Type: " + aContentType);
      if (aContentType == null)
      {
        aHttpResponse.setBadRequest ("Failed to parse Content-Type '" + sContentType + "'");
        return;
      }

      Document aSOAPDocument = null;
      ESOAPVersion eSOAPVersion = null;
      final ICommonsList <WSS4JAttachment> aIncomingAttachments = new CommonsArrayList <> ();

      final IMimeType aPlainContentType = aContentType.getCopyWithoutParameters ();
      if (aPlainContentType.equals (MT_MULTIPART_RELATED))
      {
        // MIME message
        if (s_aLogger.isDebugEnabled ())
          s_aLogger.debug ("Received MIME message");

        final String sBoundary = aContentType.getParameterValueWithName ("boundary");
        if (StringHelper.hasNoText (sBoundary))
        {
          aHttpResponse.setBadRequest ("Content-Type '" +
                                       aHttpServletRequest.getContentType () +
                                       "' misses boundary parameter");
        }
        else
        {
          if (s_aLogger.isDebugEnabled ())
            s_aLogger.debug ("MIME Boundary = " + sBoundary);

          // PARSING MIME Message via MultiPartStream
          final MultipartStream aMulti = new MultipartStream (_getRequestIS (aHttpServletRequest),
                                                              sBoundary.getBytes (StandardCharsets.ISO_8859_1),
                                                              (MultipartProgressNotifier) null);
          final IIncomingAttachmentFactory aIAF = AS4ServerSettings.getIncomingAttachmentFactory ();

          int nIndex = 0;
          while (true)
          {
            final boolean bHasNextPart = nIndex == 0 ? aMulti.skipPreamble () : aMulti.readBoundary ();
            if (!bHasNextPart)
              break;

            if (s_aLogger.isDebugEnabled ())
              s_aLogger.debug ("Found MIME part " + nIndex);
            final MultipartItemInputStream aItemIS2 = aMulti.createInputStream ();

            final MimeBodyPart aBodyPart = new MimeBodyPart (aItemIS2);
            if (nIndex == 0)
            {
              // First MIME part -> SOAP document
              final IMimeType aPlainPartMT = MimeTypeParser.parseMimeType (aBodyPart.getContentType ())
                                                           .getCopyWithoutParameters ();

              // Determine SOAP version from MIME part content type
              eSOAPVersion = ArrayHelper.findFirst (ESOAPVersion.values (),
                                                    x -> aPlainPartMT.equals (x.getMimeType ()));

              // Read SOAP document
              aSOAPDocument = DOMReader.readXMLDOM (aBodyPart.getInputStream ());
            }
            else
            {
              // MIME Attachment (index is gt 0)
              final WSS4JAttachment aAttachment = aIAF.createAttachment (aBodyPart, aResMgr);
              aIncomingAttachments.add (aAttachment);
            }
            nIndex++;
          }
        }
      }
      else
      {
        if (s_aLogger.isDebugEnabled ())
          s_aLogger.debug ("Received plain message with Content-Type " + aContentType.getAsString ());

        // Expect plain SOAP - read whole request to DOM
        // Note: this may require a huge amount of memory for large requests
        aSOAPDocument = DOMReader.readXMLDOM (_getRequestIS (aHttpServletRequest));

        // Determine SOAP version from content type
        eSOAPVersion = ArrayHelper.findFirst (ESOAPVersion.values (), x -> aPlainContentType.equals (x.getMimeType ()));
      }

      if (aSOAPDocument == null)
      {
        // We don't have a SOAP document
        if (eSOAPVersion == null)
          aHttpResponse.setBadRequest ("Failed to parse incoming message!");
        else
          aHttpResponse.setBadRequest ("Failed to parse incoming SOAP " + eSOAPVersion.getVersion () + " document!");
      }
      else
      {
        if (eSOAPVersion == null)
        {
          // Determine from namespace URI of read document as the last fallback
          final String sNamespaceURI = XMLHelper.getNamespaceURI (aSOAPDocument);
          eSOAPVersion = ArrayHelper.findFirst (ESOAPVersion.values (),
                                                x -> x.getNamespaceURI ().equals (sNamespaceURI));
        }

        if (eSOAPVersion == null)
        {
          aHttpResponse.setBadRequest ("Failed to determine SOAP version from XML document!");
        }
        else
        {
          // SOAP document and SOAP version are determined
          _handleSOAPMessage (aResMgr, aSOAPDocument, eSOAPVersion, aIncomingAttachments, aHttpResponse, aLocale);
        }
      }
    }
    catch (final Throwable t)
    {
      aHttpResponse.setResponseError (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                      "Internal error processing AS4 request",
                                      t);
    }
    finally
    {
      if (s_aDebugIncomingCB != null)
        s_aDebugIncomingCB.onRequestEnd ();

      LoggedInUserManager.getInstance ().logoutCurrentUser ();
    }
  }
}
