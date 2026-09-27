package app.warden.adb;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;

/**
 * ADB client key/cert manager for Warden's PC-free server start. The keypair is
 * generated once and persisted to filesDir so the device stays paired across
 * launches. Follows the libadb-android reference implementation.
 */
public class AdbConnectionManager extends AbsAdbConnectionManager {
    private static AdbConnectionManager INSTANCE;

    public static synchronized AbsAdbConnectionManager getInstance(Context ctx) throws Exception {
        if (INSTANCE == null) INSTANCE = new AdbConnectionManager(ctx.getApplicationContext());
        return INSTANCE;
    }

    private PrivateKey mPrivateKey;
    private Certificate mCertificate;

    private AdbConnectionManager(Context ctx) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File keyFile = new File(ctx.getFilesDir(), "adb_key.pk8");
        File certFile = new File(ctx.getFilesDir(), "adb_cert.der");
        if (keyFile.exists() && certFile.exists()) {
            mPrivateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile.toPath())));
            mCertificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(Files.newInputStream(certFile.toPath()));
        } else {
            generate();
            Files.write(keyFile.toPath(), mPrivateKey.getEncoded());
            Files.write(certFile.toPath(), mCertificate.getEncoded());
        }
    }

    private void generate() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair kp = kpg.generateKeyPair();
        PublicKey publicKey = kp.getPublic();
        mPrivateKey = kp.getPrivate();

        X500Name name = new X500Name("CN=Warden");
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 10L * 365 * 86400000L);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        X509v3CertificateBuilder builder =
                new X509v3CertificateBuilder(name, serial, notBefore, notAfter, name, spki);
        ContentSigner signer = new JcaContentSignerBuilder("SHA512withRSA").build(mPrivateKey);
        X509CertificateHolder holder = builder.build(signer);
        mCertificate = new JcaX509CertificateConverter().getCertificate(holder);
    }

    @NonNull @Override protected PrivateKey getPrivateKey() { return mPrivateKey; }
    @NonNull @Override protected Certificate getCertificate() { return mCertificate; }
    @NonNull @Override protected String getDeviceName() { return "Warden"; }
}
