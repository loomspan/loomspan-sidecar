package ai.loomspan.sdk;

import java.security.interfaces.RSAPublicKey;

/** Startup-owned RSA signer. Private material is never exposed. */
public interface SigningKeySource {
    byte[] signRs256(byte[] input);
    RSAPublicKey publicKey();
    String kid();
}
