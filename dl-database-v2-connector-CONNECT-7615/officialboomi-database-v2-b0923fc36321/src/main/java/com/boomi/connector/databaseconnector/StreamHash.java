package com.boomi.connector.databaseconnector;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.boomi.connector.databaseconnector.JavaUtility.log;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

public class StreamHash
{
    private final Logger logger;

    public StreamHash()
    {
        logger = LoggerFactory.getLogger(StreamHash.class);
    }

    public byte[] computeSHA1(InputStream stream) throws IOException
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw log(logger, new IllegalStateException("Failed to create a SHA-1 hash object"));
        }

        try (DigestInputStream digestStream = new DigestInputStream(stream, digest))
        {
            readFully(digestStream);
        }

        return digest.digest();
    }

    public String computeSHA1String(InputStream stream) throws IOException
    {
        byte[] hash = computeSHA1(stream);
        return encodeHexString(hash);
    }

    private void readFully(InputStream stream) throws IOException
    {
        @SuppressWarnings("MagicNumber") byte[] buffer = new byte[4096];
        while (true)
        {
            int count = IOUtils.read(stream, buffer);
            if (count <= 0)
            {
                break;
            }
        }
    }
}
