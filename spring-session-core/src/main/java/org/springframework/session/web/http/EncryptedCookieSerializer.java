package org.springframework.session.web.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.Assert;

public class EncryptedCookieSerializer implements CookieSerializer {

	private final SecureRandom secureRandom = new SecureRandom();

	private final static int GCM_IV_LENGTH = 12;

	private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

	private final Base64.Decoder decoder = Base64.getUrlDecoder();

	private final SecretKey key;

	private final DefaultCookieSerializer delegate = new DefaultCookieSerializer();

	public EncryptedCookieSerializer(String key) {
		Assert.notNull(key, "key cannot be null");
		boolean isExpectedLength = key.length() == 32 || key.length() == 48 || key.length() == 64;
		Assert.state(isExpectedLength, "key must be 128, 192, or 256 bits in length");
		this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
		this.delegate.setUseBase64Encoding(false);
	}

	@Override
	public void writeCookieValue(CookieValue cookieValue) {
		String value = cookieValue.getCookieValue();
		try {
			byte[] encrypted = encrypt(value, this.key, null);
			String serialized = this.encoder.encodeToString(encrypted);
			this.delegate.writeCookieValue(new CookieValue(cookieValue.getRequest(), cookieValue.getResponse(), serialized));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] encrypt(String plaintext, SecretKey secretKey, byte[] associatedData) throws Exception {

		byte[] iv = new byte[GCM_IV_LENGTH]; //NEVER REUSE THIS IV WITH SAME KEY
		secureRandom.nextBytes(iv);
		final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv); //128 bit auth tag length
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

		if (associatedData != null) {
			cipher.updateAAD(associatedData);
		}

		byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

		ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
		byteBuffer.put(iv);
		byteBuffer.put(cipherText);
		return byteBuffer.array();
	}

	/**
	 * Decrypts encrypted message (see {@link #encrypt(String, SecretKey, byte[])}).
	 *
	 * @param cipherMessage  iv with ciphertext
	 * @param secretKey      used to decrypt
	 * @param associatedData optional, additional (public) data to verify on decryption with GCM auth tag
	 * @return original plaintext
	 * @throws Exception if anything goes wrong
	 */
	private String decrypt(byte[] cipherMessage, SecretKey secretKey, byte[] associatedData) throws Exception {
		final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		//use first 12 bytes for iv
		AlgorithmParameterSpec gcmIv = new GCMParameterSpec(128, cipherMessage, 0, GCM_IV_LENGTH);
		cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);

		if (associatedData != null) {
			cipher.updateAAD(associatedData);
		}
		//use everything from 12 bytes on as ciphertext
		byte[] plainText = cipher.doFinal(cipherMessage, GCM_IV_LENGTH, cipherMessage.length - GCM_IV_LENGTH);

		return new String(plainText, StandardCharsets.UTF_8);
	}

	@Override
	public List<String> readCookieValues(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		List<String> matchingCookieValues = new ArrayList<>();
		if (cookies == null) {
			return matchingCookieValues;
		}
		for (Cookie cookie : cookies) {
			if ("SESSION".equals(cookie.getName())) {
				String value = cookie.getValue();
				byte[] decoded = this.decoder.decode(value);
				try {
					matchingCookieValues.add(decrypt(decoded, this.key, null));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return matchingCookieValues;
	}

}
