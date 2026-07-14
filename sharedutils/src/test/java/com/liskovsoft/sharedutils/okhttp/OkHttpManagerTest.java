package com.liskovsoft.sharedutils.okhttp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;

import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;

import static org.junit.Assert.*;
import com.liskovsoft.sharedutils.helpers.Helpers;

@RunWith(RobolectricTestRunner.class)
public class OkHttpManagerTest {
    @Test
    public void testSocks5ProxyConfiguration() {
        Builder builder = new Builder();
        setupProxy(builder, "127.0.0.1", "1080", null, null, Type.SOCKS);
        OkHttpClient client = builder.build();

        assertNotNull(client.proxy());
        assertEquals(Type.SOCKS, client.proxy().type());
        assertEquals(new InetSocketAddress("127.0.0.1", 1080), client.proxy().address());
    }

    private static void setupProxy(Builder builder, String host, String port, String user, String password, Proxy.Type proxyType) {
        if (host == null || port == null) {
            return;
        }

        if (user != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password.toCharArray());
                }
            });
        }

        builder.proxy(new Proxy(proxyType, new InetSocketAddress(host, Helpers.parseInt(port))));
    }
}
