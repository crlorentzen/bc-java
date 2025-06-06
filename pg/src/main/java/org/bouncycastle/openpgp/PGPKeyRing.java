package org.bouncycastle.openpgp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.KeyIdentifier;
import org.bouncycastle.bcpg.Packet;
import org.bouncycastle.bcpg.PacketFormat;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.SignaturePacket;
import org.bouncycastle.bcpg.TrustPacket;
import org.bouncycastle.bcpg.UnsupportedPacketVersionException;
import org.bouncycastle.bcpg.UserAttributePacket;
import org.bouncycastle.bcpg.UserDataPacket;
import org.bouncycastle.bcpg.UserIDPacket;

/**
 * Parent class for PGP public and secret key rings.
 */
public abstract class PGPKeyRing
{
    private static final Logger LOG = Logger.getLogger(PGPKeyRing.class.getName());

    PGPKeyRing()
    {
    }

    static TrustPacket readOptionalTrustPacket(
        BCPGInputStream pIn)
        throws IOException
    {
        int tag = pIn.skipMarkerAndPaddingPackets();

        return tag == PacketTags.TRUST ? (TrustPacket)pIn.readPacket() : null;
    }

    static List<PGPSignature> readSignaturesAndTrust(
        BCPGInputStream pIn)
        throws IOException
    {
        List<PGPSignature> sigList = new ArrayList<PGPSignature>();

        while (pIn.skipMarkerAndPaddingPackets() == PacketTags.SIGNATURE)
        {
            try
            {
                SignaturePacket signaturePacket = (SignaturePacket)pIn.readPacket();
                TrustPacket trustPacket = readOptionalTrustPacket(pIn);

                sigList.add(new PGPSignature(signaturePacket, trustPacket));
            }
            catch (UnsupportedPacketVersionException e)
            {
                // skip unsupported signatures
                if (LOG.isLoggable(Level.FINE))
                {
                    LOG.fine("skipping unknown signature: " + e.getMessage());
                }
            }
        }
        return sigList;
    }

    static void readUserIDs(
        BCPGInputStream pIn,
        List<UserDataPacket> ids,
        List<TrustPacket> idTrusts,
        List<List<PGPSignature>> idSigs)
        throws IOException
    {
        while (isUserTag(pIn.skipMarkerAndPaddingPackets()))
        {
            Packet obj = pIn.readPacket();
            if (obj instanceof UserIDPacket)
            {
                UserIDPacket id = (UserIDPacket)obj;
                ids.add(id);
            }
            else
            {
                UserAttributePacket user = (UserAttributePacket)obj;
                ids.add(new PGPUserAttributeSubpacketVector(user.getSubpackets()));
            }

            idTrusts.add(readOptionalTrustPacket(pIn));
            idSigs.add(readSignaturesAndTrust(pIn));
        }
    }

    /**
     * Return the first public key in the ring.  In the case of a {@link PGPSecretKeyRing}
     * this is also the public key of the master key pair.
     *
     * @return PGPPublicKey
     */
    public abstract PGPPublicKey getPublicKey();

    /**
     * Return an iterator containing all the public keys.
     *
     * @return Iterator
     */
    public abstract Iterator<PGPPublicKey> getPublicKeys();

    /**
     * Return the public key referred to by the passed in keyID if it
     * is present.
     *
     * @param keyID the full keyID of the key of interest.
     * @return PGPPublicKey with matching keyID.
     */
    public abstract PGPPublicKey getPublicKey(long keyID);

    /**
     * Return the public key with the passed in fingerprint if it
     * is present.
     *
     * @param fingerprint the full fingerprint of the key of interest.
     * @return PGPPublicKey with the matching fingerprint.
     */
    public abstract PGPPublicKey getPublicKey(byte[] fingerprint);

    public abstract PGPPublicKey getPublicKey(KeyIdentifier identifier);

    public abstract Iterator<PGPPublicKey> getPublicKeys(KeyIdentifier identifier);

    /**
     * Return an iterator containing all the public keys carrying signatures issued from key keyID.
     *
     * @return an iterator (possibly empty) of the public keys associated with keyID.
     */
    public abstract Iterator<PGPPublicKey> getKeysWithSignaturesBy(long keyID);

    public abstract Iterator<PGPPublicKey> getKeysWithSignaturesBy(KeyIdentifier identifier);

    /**
     * Return the number of keys in the key ring.
     *
     * @return number of keys (master key + subkey).
     */
    public abstract int size();

    public abstract void encode(OutputStream outStream)
        throws IOException;

    public abstract byte[] getEncoded()
        throws IOException;

    public abstract byte[] getEncoded(PacketFormat format)
        throws IOException;

    private static boolean isUserTag(int tag)
    {
        switch (tag)
        {
        case PacketTags.USER_ATTRIBUTE:
        case PacketTags.USER_ID:
            return true;
        default:
            return false;
        }
    }
}
