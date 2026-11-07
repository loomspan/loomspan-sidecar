# Synthetic signing vector

This fixed test-only RSA 2048-bit key was generated for interoperability tests.
It is public test data and must never protect real credentials or deployments.
`synthetic-rsa.pem` contains exactly one unencrypted PKCS#8 PRIVATE KEY.
`public-jwks.json` records its independently derived unsigned base64url n/e and
RFC 7638 SHA-256 thumbprint kid. Expected public fields are kty,n,e,kid,use,alg.
RS256 signs SHA-256 using RSA PKCS#1 v1.5. Later SDK tests may consume these files.
