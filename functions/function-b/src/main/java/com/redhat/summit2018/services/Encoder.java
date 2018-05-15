package com.redhat.summit2018.services;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

public class Encoder {


    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String encode(String url) {
        try {
            byte[] bytes = IOUtils.toByteArray(new URL(url));
            return encode(bytes);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed url " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read content from " + url, e);
        }
    }
}
