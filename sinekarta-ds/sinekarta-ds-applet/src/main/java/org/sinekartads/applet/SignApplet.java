package org.sinekartads.applet;

import java.applet.Applet;
import java.security.cert.X509Certificate;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.sinekartads.smartcard.DriverNotFoundException;
import org.sinekartads.smartcard.SmartCardAccess;
import org.sinekartads.smartcard.SmartCardUtils;
import org.sinekartads.utils.HexUtils;
import org.sinekartads.utils.JSONUtils;
import org.sinekartads.utils.X509Utils;

public class SignApplet extends Applet {

	private static final long serialVersionUID = -2886113966359858032L;
	
	SmartCardAccess sca;
	String[] matchingDrivers;
	
	@Override
	public void init ( ) {
		sca = new SmartCardAccess ( );
	}
	
	public String verifySmartCard ( String knownDriversJSON ) {
		AppletResponseDTO resp = new AppletResponseDTO ( );
		try {
			String[] knownDrivers = (String[]) JSONUtils.fromJSON(knownDriversJSON, String[].class);
			matchingDrivers = SmartCardUtils.detectDrivers(knownDrivers);
			String driver = matchingDrivers[0];
			sca.selectDriver ( driver );
			resp.setResult ( JSONUtils.toJSON(matchingDrivers) );
			resp.setResultCode(AppletResponseDTO.SUCCESS);
		} catch(Exception e) {
			processError(resp, e);
		}
		return JSONUtils.toJSON ( resp );
	}
	
	public String selectDriver ( String driver ) {
		AppletResponseDTO resp = new AppletResponseDTO ( );
		try {
			boolean missing = true;
			for ( int i=0; i<matchingDrivers.length && missing; i++ ) {
				missing = StringUtils.equalsIgnoreCase(driver, matchingDrivers[i]);
			}
			if ( missing ) {
				throw new DriverNotFoundException(String.format ( "indivalid driver", driver ));
			}
			sca.selectDriver ( driver );
			resp.setResult(driver);
			resp.setResultCode(AppletResponseDTO.SUCCESS);
		} catch(Exception e) {
			processError ( resp, e );
		}
		return JSONUtils.toJSON ( resp );
	}
	
	public String login ( String pin ) {
		AppletResponseDTO resp = new AppletResponseDTO ( );
		try {
			String[] aliases = sca.login ( pin );
			sca.logout();
			String aliasesJSON = JSONUtils.toJSON(aliases);
			resp.setResult ( aliasesJSON );
			resp.setResultCode(AppletResponseDTO.SUCCESS);
		} catch(Exception e) {
			processError ( resp, e );
		}
		return JSONUtils.toJSON ( resp );
	}
	
	public String selectCertificate(String alias) {
		AppletResponseDTO resp = new AppletResponseDTO ( );
		try {
			X509Certificate signingCertificate;
			sca.login();
			signingCertificate = sca.selectCertificate ( alias );
			sca.logout();
			resp.setResult ( X509Utils.rawX509CertificateToHex(signingCertificate) );
			resp.setResultCode(AppletResponseDTO.SUCCESS);
		} catch(Exception e) {
			processError(resp, e);
		}
		return JSONUtils.toJSON(resp);
	}
	
	public String signDigest(String digestEnc) {
		AppletResponseDTO resp = new AppletResponseDTO ( );
		try {
			byte[] digest = HexUtils.decodeHex(digestEnc);
			byte[] digitalSignature;
			sca.login();
			digitalSignature = sca.signFingerPrint(digest);
			sca.logout();
			resp.setResult ( HexUtils.encodeHex(digitalSignature) );
			resp.setResultCode(AppletResponseDTO.SUCCESS);
		} catch(Exception e) {
			processError(resp, e);
		}
		return JSONUtils.toJSON(resp);
	}
	
	public void destroy() {
		SmartCardUtils.finalizeQuietly ( sca );
	}
	
	
	// -----
	// --- Error management
	// -
	
	public void processError ( AppletResponseDTO resp, Exception e ) {
		String errorMessage = e.getMessage();
		if ( StringUtils.isBlank(errorMessage) ) {
			errorMessage = e.getClass().getName();
		}
		resp.setErrorMessage ( errorMessage );
		resp.setResultCode(AppletResponseDTO.ERROR);
		Logger.getLogger(getClass()).error(errorMessage, e);
	}
}