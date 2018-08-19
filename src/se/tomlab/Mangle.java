package se.tomlab;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Properties;

public class Mangle {
    Cipher cipher;

    public String encryptMap(Properties props) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128); // block size is 128bits
        SecretKey secretKey = keyGenerator.generateKey();

        cipher = Cipher.getInstance("AES"); //SunJCE provider AES algorithm, mode(optional) and padding schema(optional)

        props.setProperty("user", encrypt("b605td", secretKey));
        props.setProperty("passwd", encrypt("Sorkart88=", secretKey));

        props.store(new FileOutputStream("temp.props"),"UnknownProps");

        final byte[] keyData = secretKey.getEncoded();
        final String encodedKey = Base64.getEncoder().encodeToString(keyData);
        return encodedKey;
    }

    public Properties decryptMap(String secret, Properties props) {

        try {
            final byte[] keyData = Base64.getDecoder().decode(secret);
            final int keysize = keyData.length * Byte.SIZE;

            if (Cipher.getMaxAllowedKeyLength("AES") < keysize) {
                // this may be an issue if unlimited crypto is not installed
                throw new IllegalArgumentException("Key size of " + keysize
                        + " not supported in this runtime");
            }

            // throws IllegalArgumentException - if key is empty
            final SecretKeySpec aesKey = new SecretKeySpec(keyData, "AES");

            Iterator iValues=props.values().iterator();

            iValues.forEachRemaining(s -> {
                try {
                    s= (Object) decrypt((String) s,aesKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return props;
    }

    public void MangleTest() throws Exception {
    /*
     create key
     If we need to generate a new key use a KeyGenerator
     If we have existing plaintext key use a SecretKeyFactory
    */
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128); // block size is 128bits
        SecretKey secretKey = keyGenerator.generateKey();

    /*
      Cipher Info
      Algorithm : for the encryption of electronic data
      mode of operation : to avoid repeated blocks encrypt to the same values.
      padding: ensuring messages are the proper length necessary for certain ciphers
      mode/padding are not used with stream cyphers.
     */
        cipher = Cipher.getInstance("AES"); //SunJCE provider AES algorithm, mode(optional) and padding schema(optional)

        String plainText = "AES Symmetric Encryption Decryption";
        System.out.println("Plain Text Before Encryption: " + plainText);

        String encryptedText = encrypt(plainText, secretKey);
        System.out.println("Encrypted Text After Encryption: " + encryptedText);

        String decryptedText = decrypt(encryptedText, secretKey);
        System.out.println("Decrypted Text After Decryption: " + decryptedText);
    }

    public  String encrypt(String plainText, SecretKey secretKey)
            throws Exception {
        byte[] plainTextByte = plainText.getBytes();
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedByte = cipher.doFinal(plainTextByte);
        Base64.Encoder encoder = Base64.getEncoder();
        String encryptedText = encoder.encodeToString(encryptedByte);
        return encryptedText;
    }

    public String decrypt(String encryptedText, SecretKey secretKey)
            throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] encryptedTextByte = decoder.decode(encryptedText);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedByte = cipher.doFinal(encryptedTextByte);
        String decryptedText = new String(decryptedByte);
        return decryptedText;
    }
}
