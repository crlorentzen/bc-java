package org.bouncycastle.openpgp.api.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.bouncycastle.bcpg.SignatureSubpacketTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.OpenPGPTestKeys;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.api.KeyPairGeneratorCallback;
import org.bouncycastle.openpgp.api.OpenPGPApi;
import org.bouncycastle.openpgp.api.OpenPGPDetachedSignatureGenerator;
import org.bouncycastle.openpgp.api.OpenPGPDetachedSignatureProcessor;
import org.bouncycastle.openpgp.api.OpenPGPKey;
import org.bouncycastle.openpgp.api.OpenPGPSignature;
import org.bouncycastle.openpgp.api.SignatureParameters;
import org.bouncycastle.openpgp.api.SignatureSubpacketsFunction;
import org.bouncycastle.openpgp.operator.PGPKeyPairGenerator;

public class OpenPGPDetachedSignatureProcessorTest
        extends APITest
{
    @Override
    public String getName()
    {
        return "OpenPGPDetachedSignatureProcessorTest";
    }

    protected void performTestWith(OpenPGPApi api)
            throws PGPException, IOException
    {
        createVerifyV4Signature(api);
        createVerifyV6Signature(api);

        keyPassphrasesArePairedUpProperly_keyAddedFirst(api);
        keyPassphrasesArePairedUpProperly_passphraseAddedFirst(api);

        missingPassphraseThrows(api);
        wrongPassphraseThrows(api);

        withoutSigningSubkeyFails(api);
        nonSigningSubkeyFails(api);
    }

    private void createVerifyV4Signature(OpenPGPApi api)
            throws IOException, PGPException
    {
        OpenPGPDetachedSignatureGenerator gen = api.createDetachedSignature();
        gen.addSigningKey(
                api.readKeyOrCertificate().parseKey(OpenPGPTestKeys.ALICE_KEY));

        byte[] plaintext = "Hello, World!\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream plaintextIn = new ByteArrayInputStream(plaintext);

        List<OpenPGPSignature.OpenPGPDocumentSignature> signatures = gen.sign(plaintextIn);
        isEquals(1, signatures.size());
        OpenPGPSignature.OpenPGPDocumentSignature signature = signatures.get(0);
        isEquals(4, signature.getSignature().getVersion());
        String armored = signature.toAsciiArmoredString();
        isTrue(armored.startsWith("-----BEGIN PGP SIGNATURE-----\n"));

        // Verify detached signatures
        OpenPGPDetachedSignatureProcessor processor = api.verifyDetachedSignature();
        processor.addSignature(signature.getSignature());
        processor.addVerificationCertificate(api.readKeyOrCertificate().parseCertificate(OpenPGPTestKeys.ALICE_CERT));

        List<OpenPGPSignature.OpenPGPDocumentSignature> verified = processor.process(new ByteArrayInputStream(plaintext));
        isEquals(1, verified.size());
        isTrue(verified.get(0).isValid());
    }

    private void createVerifyV6Signature(OpenPGPApi api)
            throws IOException, PGPException
    {
        OpenPGPDetachedSignatureGenerator gen = api.createDetachedSignature();
        gen.addSigningKey(
                api.readKeyOrCertificate().parseKey(OpenPGPTestKeys.V6_KEY));

        byte[] plaintext = "Hello, World!\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream plaintextIn = new ByteArrayInputStream(plaintext);

        List<OpenPGPSignature.OpenPGPDocumentSignature> signatures = gen.sign(plaintextIn);
        isEquals(1, signatures.size());
        OpenPGPSignature.OpenPGPDocumentSignature signature = signatures.get(0);
        isEquals(6, signature.getSignature().getVersion());
        String armored = signature.toAsciiArmoredString();
        isTrue(armored.startsWith("-----BEGIN PGP SIGNATURE-----\n"));

        // Verify detached signatures
        OpenPGPDetachedSignatureProcessor processor = api.verifyDetachedSignature();
        processor.addSignature(signature.getSignature());
        processor.addVerificationCertificate(api.readKeyOrCertificate().parseCertificate(OpenPGPTestKeys.V6_CERT));

        List<OpenPGPSignature.OpenPGPDocumentSignature> verified = processor.process(new ByteArrayInputStream(plaintext));
        isEquals(1, verified.size());
        isTrue(verified.get(0).isValid());
    }

    private void missingPassphraseThrows(OpenPGPApi api)
    {
        isNotNull(testException(
                "Cannot unlock primary key CB186C4F0609A697E4D52DFA6C722B0C1F1E27C18A56708F6525EC27BAD9ACC9: Exception decrypting key",
                "KeyPassphraseException",
                new TestExceptionOperation()
                {
                    @Override
                    public void operation()
                            throws Exception
                    {
                        api.createDetachedSignature()
                                .addSigningKey(api.readKeyOrCertificate().parseKey(OpenPGPTestKeys.V6_KEY_LOCKED))
                                .sign(new ByteArrayInputStream("Test Data".getBytes(StandardCharsets.UTF_8)));
                    }
                }));
    }

    private void wrongPassphraseThrows(OpenPGPApi api)
    {
        isNotNull(testException(
                "Cannot unlock primary key CB186C4F0609A697E4D52DFA6C722B0C1F1E27C18A56708F6525EC27BAD9ACC9: Exception decrypting key",
                "KeyPassphraseException",
                new TestExceptionOperation()
                {
                    @Override
                    public void operation()
                            throws Exception
                    {
                        api.createDetachedSignature()
                                .addKeyPassphrase("thisIsWrong".toCharArray())
                                .addSigningKey(api.readKeyOrCertificate().parseKey(OpenPGPTestKeys.V6_KEY_LOCKED))
                                .sign(new ByteArrayInputStream("Test Data".getBytes(StandardCharsets.UTF_8)));
                    }
                }));
    }

    private void keyPassphrasesArePairedUpProperly_keyAddedFirst(OpenPGPApi api)
            throws PGPException, IOException
    {
        OpenPGPKey key = api.generateKey(new Date(), false)
                .signOnlyKey()
                .build("password".toCharArray());

        OpenPGPDetachedSignatureGenerator gen = api.createDetachedSignature();
        gen.addSigningKey(key);

        gen.addKeyPassphrase("penguin".toCharArray());
        gen.addKeyPassphrase("password".toCharArray());
        gen.addKeyPassphrase("beluga".toCharArray());

        byte[] plaintext = "arctic\ndeep sea\nice field\n".getBytes(StandardCharsets.UTF_8);
        InputStream plaintextIn = new ByteArrayInputStream(plaintext);

        List<OpenPGPSignature.OpenPGPDocumentSignature> signatures = gen.sign(plaintextIn);
        isEquals(1, signatures.size());
    }

    private void keyPassphrasesArePairedUpProperly_passphraseAddedFirst(OpenPGPApi api)
            throws PGPException, IOException
    {
        OpenPGPKey key = api.generateKey(new Date(), false)
                .signOnlyKey()
                .build("password".toCharArray());

        OpenPGPDetachedSignatureGenerator gen = api.createDetachedSignature();

        gen.addKeyPassphrase("sloth".toCharArray());
        gen.addKeyPassphrase("password".toCharArray());
        gen.addKeyPassphrase("tapir".toCharArray());

        gen.addSigningKey(key);

        byte[] plaintext = "jungle\ntropics\nswamp\n".getBytes(StandardCharsets.UTF_8);
        InputStream plaintextIn = new ByteArrayInputStream(plaintext);

        List<OpenPGPSignature.OpenPGPDocumentSignature> signatures = gen.sign(plaintextIn);
        isEquals(1, signatures.size());
    }

    private void withoutSigningSubkeyFails(OpenPGPApi api)
            throws PGPException
    {
        OpenPGPKey noSigningKey = api.generateKey()
                .withPrimaryKey(
                        new KeyPairGeneratorCallback() {
                            @Override
                            public PGPKeyPair generateFrom(PGPKeyPairGenerator generator)
                                    throws PGPException {
                                return generator.generatePrimaryKey();
                            }
                        },
                        SignatureParameters.Callback.modifyHashedSubpackets(
                                new SignatureSubpacketsFunction()
                                {
                                    @Override
                                    public PGPSignatureSubpacketGenerator apply(PGPSignatureSubpacketGenerator subpackets)
                                    {
                                        subpackets.removePacketsOfType(SignatureSubpacketTags.KEY_FLAGS);
                                        // No SIGN_DATA key flag
                                        subpackets.setKeyFlags(KeyFlags.CERTIFY_OTHER);
                                        return subpackets;
                                    }
                                }
                        )
                ).build();

        isNotNull(testException(
                "The key " + noSigningKey.getKeyIdentifier() + " does not contain any usable component keys capable of signing.",
                "InvalidSigningKeyException",
                new TestExceptionOperation()
                {
                    @Override
                    public void operation()
                            throws Exception
                    {
                        api.createDetachedSignature()
                                .addSigningKey(noSigningKey)
                                .sign(new ByteArrayInputStream("Test Data".getBytes(StandardCharsets.UTF_8)));
                    }
                }));
    }

    private void nonSigningSubkeyFails(OpenPGPApi api)
            throws PGPException
    {
        OpenPGPKey noSigningKey = api.generateKey()
                .withPrimaryKey(
                        new KeyPairGeneratorCallback() {
                            @Override
                            public PGPKeyPair generateFrom(PGPKeyPairGenerator generator)
                                    throws PGPException {
                                return generator.generatePrimaryKey();
                            }
                        },
                        SignatureParameters.Callback.modifyHashedSubpackets(
                                new SignatureSubpacketsFunction()
                                {
                                    @Override
                                    public PGPSignatureSubpacketGenerator apply(PGPSignatureSubpacketGenerator subpackets)
                                    {
                                        subpackets.removePacketsOfType(SignatureSubpacketTags.KEY_FLAGS);
                                        // No SIGN_DATA key flag
                                        subpackets.setKeyFlags(KeyFlags.CERTIFY_OTHER);
                                        return subpackets;
                                    }
                                }
                        )
                ).build();

        isNotNull(testException(
                "The primary key " + noSigningKey.getPrimaryKey().getKeyIdentifier() + " is not usable for signing.",
                "InvalidSigningKeyException",
                new TestExceptionOperation()
                {
                    @Override
                    public void operation()
                            throws Exception
                    {
                        api.createDetachedSignature()
                                .addSigningKey(noSigningKey.getPrimarySecretKey(), (char[])null, null)
                                .sign(new ByteArrayInputStream("Test Data".getBytes(StandardCharsets.UTF_8)));
                    }
                }));
    }

    public static void main(String[] args)
    {
        runTest(new OpenPGPDetachedSignatureProcessorTest());
    }
}
