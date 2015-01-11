package org.sinekartads.core.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;
import org.sinekartads.asn1.ASN1Parser.ASN1ParseException;
import org.sinekartads.asn1.ASN1Utils;
import org.sinekartads.core.CoreConfiguration;
import org.sinekartads.core.cms.BouncyCastleUtils;
import org.sinekartads.core.cms.CMSSignedDataProxyGenerator;
import org.sinekartads.core.cms.MarkedData;
import org.sinekartads.model.domain.CMSSignatureInfo;
import org.sinekartads.model.domain.DigestInfo;
import org.sinekartads.model.domain.SecurityLevel.VerifyResult;
import org.sinekartads.model.domain.SignDisposition;
import org.sinekartads.model.domain.SignatureType.SignCategory;
import org.sinekartads.model.domain.TimeStampInfo;
import org.sinekartads.model.domain.Transitions.ChainSignature;
import org.sinekartads.model.domain.Transitions.DigestSignature;
import org.sinekartads.model.domain.Transitions.EmptySignature;
import org.sinekartads.model.domain.Transitions.FinalizedSignature;
import org.sinekartads.model.domain.Transitions.MarkedSignature;
import org.sinekartads.model.domain.Transitions.SignedSignature;
import org.sinekartads.model.domain.Transitions.VerifiedSignature;
import org.sinekartads.model.domain.TsRequestInfo;
import org.sinekartads.model.domain.TsResponseInfo;
import org.sinekartads.model.domain.VerifyInfo;
import org.sinekartads.model.oid.DigestAlgorithm;
import org.sinekartads.model.oid.EncryptionAlgorithm;
import org.sinekartads.model.oid.SignatureAlgorithm;
import org.sinekartads.model.oid.SinekartaDsObjectIdentifiers;
import org.springframework.util.Assert;

public class CMSSignatureService 
		extends AbstractSignatureService < SignCategory,
										   SignDisposition.CMS,
										   CMSSignatureInfo > {

	private static final Logger tracer = Logger.getLogger(CMSSignatureService.class);

	private static final JcaX509CertificateConverter certHolderConverter = 
			new JcaX509CertificateConverter().setProvider ( 
					CoreConfiguration.getInstance().getProviderName() );

	
	// -----
	// --- Pre-Sign phase
	// -
	
	public DigestSignature < SignCategory, 
							 SignDisposition.CMS, 
							 VerifyResult, 		
							 CMSSignatureInfo >	doPreSign (	ChainSignature < SignCategory, 
									 										 SignDisposition.CMS, 
									 										 VerifyResult, 		
									 										 CMSSignatureInfo > chainSignature,
															InputStream 						contentIs )
													 							 
																					throws SignatureException, IOException {
		
		// Receive a chainSignature (trusted or untrusted) and the content to be signed
		Assert.notNull ( chainSignature );
		Assert.notNull ( contentIs );
		
		// Prepare the signature variables
		DigestSignature 	  	  < SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			digestSignature	= null;
		
		// Extract the signature options from the chainSignature
		DigestAlgorithm digestAlgorithm 	= chainSignature.getDigestAlgorithm();
		SignDisposition.CMS disposition 	= chainSignature.getDisposition();
		
		// Extract the trusted certificate chain from the trustedChainSignature
		X509Certificate[] trustedChain = chainSignature.getRawX509Certificates();
		
		// Evaluate the digestInfo as digestAlgorithm.evalDigest( <content + certificate.chain> )  
		DigestInfo digestInfo;
		if( !digestAlgorithm.matchesWith(DigestAlgorithm.SHA256) ) {
			// TODO configure the generator to use other signAlgorithms and digestAlgorithms
			throw new IllegalArgumentException("only SHA256 is currently supported");
		}
		try {
			CMSProcessable processable = new CMSProcessableByteArray ( IOUtils.toByteArray(contentIs) );
			CMSSignedDataProxyGenerator generator = new CMSSignedDataProxyGenerator ( );
			generator.embedCertificateChain ( trustedChain );
			byte[] fingerPrint = generator.evaluateDigest ( processable, disposition );
			digestInfo = DigestInfo.getInstance ( digestAlgorithm, fingerPrint );
			chainSignature.setSigningTime(generator.getSigningTime());
		} catch (CertificateEncodingException e) {
			// never thrown, certificate validity granted by CertificateInfo
			throw new RuntimeException(e);
		} catch (CMSException e) {
			throw new SignatureException(e);
		}		
		
		// Add the digestInfo to the trustedChainSignature and obtain the digestSignature 
		digestSignature = chainSignature.toDigestSignature( digestInfo );
		
		// Return the digestSignature
		return digestSignature;
	}

	
	
	// -----
	// --- Post-Sign phase
	// -
	
	@Override
	public FinalizedSignature < SignCategory, 
							 	SignDisposition.CMS, 
							 	VerifyResult, 		
							 	CMSSignatureInfo > 		doPostSign (	SignedSignature	  <	SignCategory, 
													 	 						 		SignDisposition.CMS, 
													 	 						 	 	VerifyResult, 		
													 	 						 	 	CMSSignatureInfo 		 >	signedSignature,
																	InputStream 									contentIs,
																	OutputStream 									detachedSignOs,
																	OutputStream 									embeddedSignOs,
																	OutputStream 									tsResultOs,
																	OutputStream 									markedSignOs 	)
																			
																			throws SignatureException, IOException 			{
		
		// Receive a signedSignature, the content to be signed and some outputStreams for storing the sign products to;  
		//			the digitalSignature has already been generated externally in some way
		// Depending by the signature disposition, the needed outputStreams must have been provided or some assertions will 
		//			fail: use throw-away ByteArrayOutputStreams instances if you are not interested to save any sign product
		Assert.notNull ( signedSignature );
		Assert.notNull ( contentIs );
		
		// Prepare the signature variables
		MarkedSignature   		  <	SignCategory, 
							 		SignDisposition.CMS,
							 		VerifyResult,
							 		CMSSignatureInfo >			markedSignature	= null;
		
		FinalizedSignature 	  	  <	SignCategory, 
	 								SignDisposition.CMS,
	 								VerifyResult,
	 								CMSSignatureInfo >			finalizedSignature = null;
		
		// Extract the signature options from the digestSignature
		SignDisposition.CMS signDisposition = signedSignature.getDisposition();
		SignatureAlgorithm signAlgorithm = signedSignature.getSignAlgorithm();
		X509Certificate[] trustedChain = signedSignature.getRawX509Certificates();
		if( !signAlgorithm.matchesWith(SinekartaDsObjectIdentifiers.asgn_SHA256withRSA) ) {
			// TODO configure the generator to use other signAlgorithms and digestAlgorithms
			throw new UnsupportedOperationException ( "only SHA256WithRSA is supported right now" );
		}
				
		// Load the content to be signed
		byte[] content = IOUtils.toByteArray(contentIs); 
		CMSProcessable processable = new CMSProcessableByteArray(content);
		CMSSignedData signedData;		
		
		// Embed the content and the certificate chain into the signedData
		byte[] signedDataEnc = null;
		try {
			CMSSignedDataProxyGenerator generator = new CMSSignedDataProxyGenerator();
			generator.setSigningTime(signedSignature.getSigningTime());
			generator.embedCertificateChain(trustedChain);
			signedData = generator.generateSignedData(
					processable, signedSignature.getDigitalSignature(), signDisposition );
			signedDataEnc = signedData.getEncoded();
		} catch (CMSException e) {
			throw new SignatureException (e);
		} catch (CertificateEncodingException e) {
			// never thrown, certificate validity granted by CertificateInfo
			throw new RuntimeException(e);
		}
		
		// Store the signedData into the appropriated OutputStream:
		//		- detachedSignOs	for DETACHED dispositions, *.p7m
		//		- embeddedSignOs 	for EMBEDDED dispositions, *.p7m
		// Init the inputStreams for the next applyTimeStamp() call
		InputStream detachedSignIs;
		InputStream embeddedSignIs;
		switch ( signDisposition ) {
			case DETACHED: {
				Assert.notNull ( detachedSignOs );
				IOUtils.write(signedDataEnc, detachedSignOs);
				
				contentIs = new ByteArrayInputStream ( content );
				detachedSignIs = new ByteArrayInputStream ( signedDataEnc );
				embeddedSignIs = null;
				break;
			}
			case EMBEDDED: {
				Assert.notNull ( embeddedSignOs );
				IOUtils.write(signedDataEnc, embeddedSignOs);
				
				contentIs = null;
				detachedSignIs = null;
				embeddedSignIs = new ByteArrayInputStream ( signedDataEnc );
				break;
			}
			default: {
				throw new UnsupportedOperationException(String.format (
						"unsupported signature disposition - %s", signDisposition));
			}
		}

		// Call applyTimeStamp() to apply the timeStamp if expected by the signatureType, the resulting structure will 
		//			be sent through timestampOs or markedSignOs contextually to the application of the timeStamp
		TsRequestInfo tsRequest = signedSignature.getTsRequest();
		if ( tsRequest != null ) {
			markedSignature = signedSignature.toMarkedSignature();
			TimeStampInfo timeStamp = doApplyTimeStamp ( 
					tsRequest, 
					contentIs,
					detachedSignIs,
					embeddedSignIs,
					tsResultOs,
					markedSignOs );
			markedSignature.appendTimeStamp ( timeStamp, tsRequest.getDisposition() );
			finalizedSignature = markedSignature.finalizeSignature();
		} else {
			finalizedSignature = signedSignature.finalizeSignature();
		}
		
		return finalizedSignature;
	}

	
	
	// -----
	// --- TimeStamp application

	@SuppressWarnings({ "unchecked", "rawtypes", "resource" })
	@Override
	public TimeStampInfo doApplyTimeStamp (
					TsRequestInfo tsRequest,
					InputStream contentIs,
					InputStream signatureIs,
					InputStream embeddedSignIs,
					OutputStream timestampOs,
					OutputStream markedSignOs ) 
					throws SignatureException,
							IOException, 
							IllegalArgumentException {
		
		Assert.notNull ( tsRequest );
		
		// Generate the signedData from signedDataIs or embeddedSignIs
		CMSSignedData signedData;
		byte[] signedDataEnc;
        try {
        	InputStream signedDataIs;
        	if ( embeddedSignIs != null ) {
        		signedDataIs = embeddedSignIs;
        	} else if ( signatureIs != null ) {
        		signedDataIs = signatureIs;
        	} else {
        		throw new IllegalArgumentException( "unable to retrieve the signedData, provide embeddedSignIs or signatureIs" );
        	}
	        if ( contentIs == null ) {
				signedData = new CMSSignedData ( signedDataIs );
	        } else {
	        	byte[] content = IOUtils.toByteArray ( contentIs );
	            signedData = new CMSSignedData ( new CMSProcessableByteArray(content), signedDataIs );
	        }
	        signedDataEnc = signedData.getEncoded();
        } catch(Exception e) {
        	throw new SignatureException("unable to build a signedData from the given inputStreams, has contentIs been provided?", e);
        }
        
		// Store the signedData into the appropriated OutputStream if provided:
		//		- timestampOs 		for DETACHED dispositions, rawTimeStampResponse as *.tsr
		//		- markedDataOs 		for ENVELOPING dispositions, Dike-like format as *.tsa
		//		- markedDataOs 		for ATTRIBUTE dispositions, TS added as unsigned attribute, *.m7m
        TsResponseInfo tsResponse;
		switch ( tsRequest.getDisposition() ) {
			case DETACHED: {
				throw new UnsupportedOperationException();
				// Store the rawTimeStampResponse into the timestampOs (*.tsr)
//				Assert.notNull ( timestampOs );
//				IOUtils.write ( encTimeStampResponse, timestampOs );
//				break;
			}
			case ENVELOPING: {
		        // Evaluate the message imprint digest for the tsRequest
		        TsRequestInfo digestTsr = tsRequest.evaluateMessageImprint( signedDataEnc );
		        
		        // Use timeStampService to receive the tsResponse
		        byte[] encTimeStampToken;
				tsResponse = timeStampService.processTsTequest ( digestTsr );
				encTimeStampToken = tsResponse.getTimeStamp().getEncTimeStampToken();

				// Generate a MarkedData as <signedDataEnc + rawTimeStampToken> and convert it to bytes (markedDataEnc)
				byte[] markedDataEnc = null;
				try {
					MarkedData markedData = MarkedData.getInstance ( signedDataEnc, encTimeStampToken );
					markedDataEnc = markedData.getEncoded();
				} catch (TSPException | CMSException e) {
					throw new SignatureException(String.format("unable to apply the timestamp - %s", e.getMessage()), e);
				} catch (ASN1ParseException e) {
					throw new SignatureException(e);
				}
				
				// Write the markedDataEnc into the markedSignOs (*.tsa)
				Assert.notNull ( markedSignOs );
				IOUtils.write(markedDataEnc, markedSignOs);
				break;
			} case ATTRIBUTE: {
				tsResponse = null;
		        SignerInformationStore markedStore;
		        // Generate a new signerStore and fill it with the marked signers
				Collection<SignerInformation> prevSigners = signedData.getSignerInfos().getSigners();
				List<SignerInformation> markedSigners = new ArrayList<SignerInformation>();
		        for(SignerInformation signer : prevSigners) {
		        	// Evaluate the message imprint digest for the tsRequest
		            TsRequestInfo digestTsr = tsRequest.evaluateMessageImprint( signer.getSignature() );
		            byte[] encTimeStampToken;
		    		tsResponse = timeStampService.processTsTequest ( digestTsr );
		    		encTimeStampToken = tsResponse.getTimeStamp().getEncTimeStampToken();
		                    
                    AttributeTable unsigned = signer.getUnsignedAttributes();
                    Hashtable<ASN1ObjectIdentifier, Attribute> unsignedAttrs = null;
                    if (unsigned == null) {
                        unsignedAttrs = new Hashtable<ASN1ObjectIdentifier, Attribute>();
                    } else {
                        unsignedAttrs = signer.getUnsignedAttributes().toHashtable();
                    }

                    Attribute attrTimeStamp = new Attribute ( 
                    		PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, 
                    		new DERSet(ASN1Utils.readObject(encTimeStampToken)) );
                    unsignedAttrs.put(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, attrTimeStamp);

                    SignerInformation markedSigner = SignerInformation.replaceUnsignedAttributes(signer, new AttributeTable(unsignedAttrs));
		                    
		            markedSigners.add(markedSigner);
		        }
		        markedStore = new SignerInformationStore(markedSigners);
		        
		        // Generate a new, marked, signedData by replacing the signerStore and convert it to bytes (markedDataEnc) 
		        CMSSignedData markedData = CMSSignedData.replaceSigners(signedData, markedStore);
		        byte[] markedDataEnc = markedData.getEncoded();
		        
		        // Write the markedDataEnc into the markedSignOs (*.m7m)
		        Assert.notNull ( markedSignOs );
				IOUtils.write(markedDataEnc, markedSignOs);
		        break;
			} default: {
				throw new UnsupportedOperationException(String.format ( 
						"%s timestamp disposition not supported by this method, use MarkedDataService instead", 
						tsRequest.getDisposition()));
			} 
		}
		
		// Return the validated TimeStamp
		return tsResponse.getTimeStamp();
	}
	
	
	
	// -----
	// --- Verification
	// -

//	public VerifyInfo verify (
//			InputStream contentIs,
//			InputStream envelopeIs ) 
//					throws 	CertificateException,
//							SignatureException,
//							TSPException, 
//							IOException {
//		
//		// DETACHED signature disposition, ATTRIBUTE timeStamp disposition or no timeStamp 
//		return verify(contentIs, null, envelopeIs, null);
//	}
//	
//	public VerifyInfo verify (
//			InputStream contentIs,
//			InputStream tsResponseIs,
//			InputStream envelopeIs ) 
//					throws 	CertificateException,
//							SignatureException,
//							TSPException, 
//							IOException {
//		
//		// DETACHED signature disposition, DETACHED timeStamp disposition
//		return verify(contentIs, tsResponseIs, envelopeIs, null);
//	}
//	
//	public VerifyInfo verify ( 
//			InputStream envelopeIs, 
//			OutputStream contentOs ) 
//					throws 	CertificateException,
//							SignatureException,
//							TSPException, 
//							IOException {
//		
//		// EMBEDDED signature disposition, ENVELOPING or ATTRIBUTE timeStamp disposition
//		return verify(null, null, envelopeIs, contentOs);
//	}
	
	@Override
	public VerifyInfo doVerify ( 
			InputStream contentIs,
			InputStream tsResponseIs,
			InputStream envelopeIs,
			VerifyResult requiredSecurityLevel,
			OutputStream contentOs ) 
					throws 	CertificateException,
							SignatureException,
							IOException {
		
		// General envelope verification, supported by external data if needed. At least envelopeIs and contentOs 
		//			need to be provided, use a ByteArrayOutputStream if you are note interested to store the content 
		Assert.notNull ( envelopeIs );
		Assert.notNull ( contentOs );
		Assert.notNull ( requiredSecurityLevel );
		
		
		// Prepare the signature variables
		EmptySignature   		  < SignCategory, 
							 		SignDisposition.CMS,
							 		VerifyResult,
							 		CMSSignatureInfo >			emptySignature = null;		
							
		ChainSignature 	  		  < SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			chainSignature = null;
							
		DigestSignature 	  	  < SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			digestSignature	= null;		
							
		SignedSignature 	  	  < SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			signedSignature	= null;
		
		MarkedSignature   		  <	SignCategory, 
							 		SignDisposition.CMS,
							 		VerifyResult,
							 		CMSSignatureInfo >			markedSignature	= null;

		FinalizedSignature 	  	  <	SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			finalizedSignature = null;

		VerifiedSignature 	  	  <	SignCategory, 
									SignDisposition.CMS,
									VerifyResult,
									CMSSignatureInfo >			verifiedSignature = null;		
		byte[] content;
		MarkedData markedData = null;
		CMSSignedData signedData = null;
		SignCategory signatureType;
		// ---------------------------------------------------------------------------------
		// --- FIXME signatureDisposition currently frozen --------------------------------
		// ---------------------------------------------------------------------------------
		// --- To re-enable it just defroze the assignment code and restore it into the 
		// --- SignatureInfo constructor
//		SignDisposition.CMS signatureDisposition;
		// ----------------------------------------------------------------------------------
		
		TimeStampToken rawTimeStampToken = null;
		TimeStampInfo externalTimeStamp = null;
		SignDisposition.TimeStamp extTsDisposition = null;
		
		// Retrieve the external timeStamp, it will considered applied to any signature on the document
		//		- from the MarkedData structure loaded from the envelopedIs, if matching
		//		- from the timeStamp loaded from the tsResponseIs, if provided
		byte[] envelope = IOUtils.toByteArray ( envelopeIs ); 
		try {
			// ENVELOPING timeStamp disposition + EMBEDDED signature disposition
			// 			try to generate a markedData: if fails attempt to get a CMSSignedData
			markedData = MarkedData.getInstance ( envelope );
			
			// If the markedData generation succeeds, extract the nested signedData and the verified extTimestamp
			signedData = markedData.getNestedSignedData ( );
			rawTimeStampToken = markedData.getRawTimeStampToken ( );
			extTsDisposition = SignDisposition.TimeStamp.ENVELOPING;
			externalTimeStamp = timeStampService.verify ( rawTimeStampToken );
		} catch(ASN1ParseException e) {		
			// do nothing, try with the next disposition
		}
		if ( tsResponseIs != null ) {
			// DETACHED timestamp disposition / still unable to determinate the signature disposition   
			// 			load the rawTimeStampResponse from the tsResponseIs, verify the extTimestamp then
			try {
				TimeStampResponse rawTimeStampResponse = new TimeStampResponse ( tsResponseIs );
				rawTimeStampToken = rawTimeStampResponse.getTimeStampToken();
				externalTimeStamp = timeStampService.verify ( rawTimeStampToken );
				extTsDisposition = SignDisposition.TimeStamp.DETACHED;
			} catch(TSPException e) {
				
			}
		}
		
		if ( markedData == null ) {
			// ATTRIBUTE timeStamp disposition - no extTimestamp found  
			try { 
				// EMBEDDED signature disposition 
				//			try to load the CMSSignedData directly from envelopeIs, if fails go to DETACHED
				signedData = new CMSSignedData(envelope);
//				signatureDisposition = SignDisposition.CMS.EMBEDDED;
			} catch(Exception e) {
				// DETACHED signature disposition
				//			build the CMSSignedData from the contents loaded from envelopeIs and contentIs
				try {
					Assert.notNull ( contentIs );
					content = IOUtils.toByteArray ( contentIs );
		            signedData = new CMSSignedData ( new CMSProcessableByteArray(content), envelope );
//		            signatureDisposition = SignDisposition.CMS.DETACHED;
				} catch (CMSException cmse) {
					throw new IllegalArgumentException ( "unable to load a cms signature from the envelope" );
				}
			}
		} else {
//			signatureDisposition = SignDisposition.CMS.EMBEDDED;
		}
		
		// Verify the digital signatures and the timeStamps contained into the CMSSignedData.
		// The VerifyInfo object, output of this operation, will contain the list of the SignatureInfo 
		// 			and TimestampInfo detected with the relative verification results.
		VerifyInfo verifyInfo = new VerifyInfo();

		// Extract the content from the signedData and store it into the contentOs
		content = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
			CMSProcessable signedContent = signedData.getSignedContent();
			signedContent.write(baos);
			baos.flush();
			content = baos.toByteArray();
			
		} catch(CMSException e) {
			throw new SignatureException(e);
		}
		
	 	// Extract the stores from the signedData: signerInformations and certHolders
		SignerInformationStore signerStore = signedData.getSignerInfos();
	 	Store certHolderStore = signedData.getCertificates();
		
		// Iterate over the signers and build a SignatureInfo each, the timeStamp eventually 
		//			stored as an unsigned attribute will be added to the signature as an TimeStampInfo
	 	@SuppressWarnings("unchecked")
	 	Collection<SignerInformation> signers = signerStore.getSigners();
		DigestAlgorithm digestAlgorithm;
		EncryptionAlgorithm encryptionAlgorithm;
		SignatureAlgorithm signatureAlgorithm;
		SignerId signerId;
		MessageDigest digester;
		byte[] fingerPrint;
		DigestInfo digest;
		byte[] digitalSignature;
		X509CertificateHolder certHolder;
		AttributeTable unsignedAttrs;
		Attribute tsAttribute;
		TimeStampInfo attributeTimeStamp;
	 	VerifyResult securityLevel = VerifyResult.VALID;
	 	VerifyResult minSecurityLevel = VerifyResult.VALID;
	 	// Iterate over the CMS signature descriptor (SignerInformation)
		for ( SignerInformation signer : signers ) {	
			// Evaluate the signatureAlgorithm 
			digestAlgorithm = DigestAlgorithm.getInstance(signer.getDigestAlgOID());
			encryptionAlgorithm = EncryptionAlgorithm.getInstance(signer.getEncryptionAlgOID());
			signatureAlgorithm = SignatureAlgorithm.getInstance(digestAlgorithm, encryptionAlgorithm);
			
            // Extract and verify the attributeTimeStamp, if any
            attributeTimeStamp = null;
            unsignedAttrs = signer.getUnsignedAttributes();
        	if (unsignedAttrs != null) {
        		tsAttribute = (Attribute) unsignedAttrs.toHashtable().get ( SinekartaDsObjectIdentifiers.attr_timeStampToken );
        		if ( tsAttribute != null ) {
        			rawTimeStampToken = (TimeStampToken) tsAttribute.getAttrValues().getObjects().nextElement();
        			attributeTimeStamp = timeStampService.verify (
        					rawTimeStampToken, 
        					new ByteArrayInputStream(content) );
        		}
        	}
			
			// Recognize the signature type, the disposition is already known
//        	if ( externalTimeStamp == null && attributeTimeStamp == null ) {
//        		signatureType = SignCategory.CAdES_BES; 
//        	} else {
//        		signatureType = SignCategory.CAdES_T;
//        	}
        	signatureType = SignCategory.CMS;
        	// TODO manage the other CMS signature types
            
            // Instance the CMSSignatureInfo as emptySignature 
        	emptySignature = new CMSSignatureInfo ( signatureAlgorithm, digestAlgorithm );
			
        	// Obtain the trusted signing certificate and generate the trustedChainSignature
			signerId = signer.getSID();
			certHolder = (X509CertificateHolder) certHolderStore.getMatches(signerId).iterator().next();
			chainSignature = emptySignature.toChainSignature ( 
					new X509Certificate[] {certHolderConverter.getCertificate(certHolder)} );
        	
        	// Evaluate the digest by using the digestAlgorithm on the content and generate the digestSignature
			try {
				digester = MessageDigest.getInstance ( digestAlgorithm.getName() );
				fingerPrint = digester.digest ( content );
				digest = DigestInfo.getInstance(digestAlgorithm, fingerPrint);
			} catch (NoSuchAlgorithmException e) {
				// never thrown - algorithm validity granted by DigestAlgorithm
				throw new RuntimeException(e);
			}
			digestSignature = chainSignature.toDigestSignature ( digest ); 
			
			// Obtain the digitalSignature and generate the signedSignature
			digitalSignature = signer.getSignature();
			signedSignature = digestSignature.toSignedSignature(digitalSignature);
            
			// Finalize the signature
			if ( signatureType.isTimeStamped() ) {
				// Convert the signedSignature to markedSignature, append the timeStamps to it and finalize,
				//			if the signatureType is timeStamped
				markedSignature = signedSignature.toMarkedSignature();
				if ( externalTimeStamp != null ) {
					markedSignature.appendTimeStamp(externalTimeStamp, extTsDisposition);
				}
				if ( attributeTimeStamp != null ) {
					markedSignature.appendTimeStamp(attributeTimeStamp, SignDisposition.TimeStamp.ATTRIBUTE);
				}
				finalizedSignature = markedSignature.finalizeSignature();
			} else {
				// Finalize the signedSignature otherwise 
				finalizedSignature = signedSignature.finalizeSignature();
			}

            // Verify the digital signature and choose the appropriated verifyResult
            try {
            	SignerInformationVerifier verifier = BouncyCastleUtils.buildVerifierFor ( certHolder );
            	if ( signer.verify(certHolderConverter.getCertificate(certHolder), "BC") ) {
//            	if ( signer.verify(verifier) ) {
        			securityLevel = minLevel ( securityLevel, VerifyResult.VALID );
            	} else {
            		securityLevel = VerifyResult.INVALID;
            	}
			} catch (Exception e) {
				tracer.error ( String.format("unable to get a verifier for %s", certHolder), e );
				securityLevel = VerifyResult.INVALID;
			}
            verifiedSignature = finalizedSignature.toVerifiedSignature(securityLevel);
            
            // Add the signatureInfo inside the verifyInfo
            verifyInfo.addSignature(verifiedSignature);
            
            // Evaluate the current minSecurityLevel
            if ( minSecurityLevel.compareTo(securityLevel) < 0 ) {
            	minSecurityLevel = securityLevel; 
            }
		}
		verifyInfo.setMinSecurityLevel(minSecurityLevel);
		
		// Write the signedContent to the contentOs, if any
		if ( securityLevel.compareTo(requiredSecurityLevel) >= 0 ) { 
			IOUtils.copy( new ByteArrayInputStream(content), contentOs );
		}
		
		return verifyInfo;
	}

}
