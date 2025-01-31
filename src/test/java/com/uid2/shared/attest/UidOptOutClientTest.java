package com.uid2.shared.attest;

import com.uid2.shared.cloud.CloudUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.http.HttpClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UidOptOutClientTest {
    private Proxy proxy = CloudUtils.defaultProxy;
    private AttestationTokenRetriever mockAttestationTokenRetriever = mock(AttestationTokenRetriever.class);
    private HttpClient mockHttpClient = mock(HttpClient.class);
    private UidOptOutClient optOutClient;

    @BeforeEach
    void setUp() {
        when(mockAttestationTokenRetriever.getOptOutJWT()).thenReturn("optOutJWT");
        when(mockAttestationTokenRetriever.getCoreJWT()).thenReturn("coreJWT");

        optOutClient = new UidOptOutClient(
                "userToken", proxy,
                true, mockAttestationTokenRetriever, mockHttpClient);
    }

    @Test
    void getJwtReturnsOptOutToken() {
        Assertions.assertEquals("optOutJWT", this.optOutClient.getJWT());
    }
}
