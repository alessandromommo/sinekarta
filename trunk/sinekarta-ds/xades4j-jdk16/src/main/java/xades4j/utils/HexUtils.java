package xades4j.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.Conversion;
import org.apache.commons.lang3.StringUtils;


/*
 * Copyright (C) 2010 - 2012 Jenia Software.
 *
 * This file is part of Sinekarta
 *
 * Sinekarta is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sinekarta is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */


public class HexUtils {

	// -----
	// --- Hex encode: 			byte[] | boolean[] | long -> String
	// -
	
	/**
	 * converting a byte[] to it's hexadecimal format String
	 * 
	 * @param buf the bytes to convert
	 * @return the hex String corresponding to buf
	 */
	public static String encodeHex (byte[] buf) {
		if (buf==null) return null;
		return Hex.encodeHexString(buf);
	}
	
	/**
	 * converting a boolean[] to it's hexadecimal format String
	 * 
	 * @param buf the bytes to convert
	 * @return the hex String corresponding to buf
	 */
	public static String encodeHex (boolean ... buf) {
		if (buf==null) return null;
		final int bytesNumber = buf.length/8;
		final int offset = buf.length%8;
		byte[] bytes = new byte[bytesNumber];
		byte tmpByte;
		for(int block=bytesNumber-1; block>=0; block--) {
			tmpByte = 0;
			for(int bit=8-1; bit>=0; bit--) {
				tmpByte += buf[block*8+bit-offset] ? 1 : 0;
				tmpByte = Integer.valueOf(tmpByte<<8).byteValue();
			}
			bytes[block] = tmpByte;
		}
		// TODO verify that works, i.e. encodeHex(true,false,false,true) -> "09"
		return Hex.encodeHexString(bytes);
	}

	public static String encodeHex (long value) {
		long tmpL = value;
		int bytes = 0;
		while (bytes<Long.SIZE && tmpL>0) {
			tmpL = tmpL>>8;
			bytes++;
		}
		return encodeHex(value, bytes);
	}	
	
	public static String encodeHex (long value, int bytes) {
        return encodeHex(Conversion.longToByteArray(value, 0, new byte[bytes], 0, bytes));        
	}
	
	
	
	// -----
	// --- Hex decode: 			String -> byte[]
	// -
	
	/**
	 * converting a hexadecimal format String to corresponding byte[]
	 * @param hex the hexadecimal format String
	 * @return corresponding byte[]
	 */
	public static byte[] decodeHex (String hex) {
		if ( StringUtils.isBlank(hex) ) 										return null;
		byte[] ret;
			try {
				ret = Hex.decodeHex(hex.toCharArray());
			} catch (DecoderException e) {
				// never thrown if target hex has been generated by any HexUtility
				throw new RuntimeException(e);
			}
		return ret;
	}
	
	public static InputStream decodeHexToInputStream ( String hex ) {
		if ( StringUtils.isBlank(hex) )											return null;
		byte[] content = HexUtils.decodeHex ( hex ); 
		return new ByteArrayInputStream ( content );
	}
	
	
	
	// -----
	// --- Random hex generation
	// -
	
	public static String randomHex(int length) {
		StringBuilder buf = new StringBuilder();
		for(int i=0; i<length; i++) {
			buf.append(Long.toHexString((long)(Math.random()*16)));
		} 
		return buf.toString();
	}

}
