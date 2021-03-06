package com.danlind.igz.brokerapi;

import com.danlind.igz.Zorro;
import com.danlind.igz.adapter.RestApiAdapter;
import com.danlind.igz.adapter.StreamingApiAdapter;
import com.danlind.igz.config.PluginProperties;
import com.danlind.igz.domain.types.AccountType;
import com.danlind.igz.handler.LoginHandler;
import com.danlind.igz.ig.api.client.RestAPI;
import com.danlind.igz.ig.api.client.StreamingAPI;
import com.danlind.igz.ig.api.client.rest.AuthenticationResponseAndConversationContext;
import com.danlind.igz.ig.api.client.rest.ConversationContextV3;
import com.danlind.igz.ig.api.client.rest.dto.session.createSessionV3.AccessTokenResponse;
import com.lightstreamer.ls_client.ConnectionListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Created by danlin on 2017-04-08.
 */
@PrepareForTest(Zorro.class)
@RunWith(PowerMockRunner.class)

public class BrokerLoginTest {

    @Mock
    RestAPI restApi;

    @Mock
    LoginHandler loginHandler;

    @Mock
    StreamingAPI streamingAPI;

    @Mock
    PluginProperties pluginProperties;

    @Mock
    ConnectionListener connectionListener;

    @InjectMocks
    RestApiAdapter restApiAdapter;

    @InjectMocks
    StreamingApiAdapter streamingApiAdapter;

    BrokerLogin brokerLogin;

    @Before
    public void setUp() throws Exception {
        brokerLogin = Mockito.spy(new BrokerLogin(streamingApiAdapter, restApiAdapter, pluginProperties));
        PowerMockito.mockStatic(Zorro.class);
        PowerMockito.when(Zorro.class,"callProgress",anyInt()).thenReturn(1);
        PowerMockito.doNothing().when(Zorro.class,"indicateError");

        AuthenticationResponseAndConversationContext context = AuthenticationResponseAndConversationContext.builder()
            .accountId("TestAccountId")
            .conversationContext(mock(ConversationContextV3.class))
            .lightstreamerEndpoint("TestLightstreamerEndpoint")
            .build();

        AccessTokenResponse accessTokenResponse = new AccessTokenResponse();
        accessTokenResponse.setExpires_in("10000");

        when(restApi.createSessionV3(any(), any())).thenReturn(context);
        when(restApi.refreshSessionV1(any(), any())).thenReturn(accessTokenResponse);
        when(streamingAPI.connect(anyString(), any(), anyString())).thenReturn(connectionListener);
        when(pluginProperties.getRefreshTokenInterval()).thenReturn(30000);
    }

    @Test
    public void testConnectSucceeded() throws Exception {
        assertEquals(1,brokerLogin.connect("TestId", "TestPassword", "Real"));
        assertEquals("TestAccountId", brokerLogin.getAccountId());
        assertEquals(AccountType.Real, brokerLogin.getZorroAccountType());
    }

    @Test
    public void testConnectFailedWithHttpException() throws Exception {
        when(restApi.createSessionV3(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        assertEquals(0,brokerLogin.connect("TestId", "TestPassword", "Real"));
    }

    @Test
    public void testConnectFailedWithOtherException() throws Exception {
        when(restApi.createSessionV3(any(), any())).thenThrow(new RuntimeException());
        assertEquals(0,brokerLogin.connect("TestId", "TestPassword", "Real"));
    }

    @Test
    public void testRefreshToken() throws Exception {
        when(pluginProperties.getRefreshTokenInterval()).thenReturn(300);
        assertEquals(1,brokerLogin.connect("TestId", "TestPassword", "Real"));
        Thread.sleep(550);
        verify(restApi, atLeastOnce()).refreshSessionV1(any(), any());
        verify(brokerLogin, atMost(0)).disconnect();
        brokerLogin.disconnect();
    }

    @Test
    public void testExceptionOnRefreshToken() throws Exception {
        when(pluginProperties.getRefreshTokenMaxRetry()).thenReturn(2);
        when(pluginProperties.getRefreshTokenRetryInterval()).thenReturn(50);
        when(pluginProperties.getRefreshTokenInterval()).thenReturn(300);
        when(restApi.refreshSessionV1(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "", "{\"errorCode\":\"error.security.oauth-token-invalid\"}".getBytes(), Charset.defaultCharset()));
        assertEquals(1,brokerLogin.connect("TestId", "TestPassword", "Real"));
        Thread.sleep(500);
        verify(brokerLogin, atLeastOnce()).disconnect();
    }

    @Test
    public void testDisconnect() throws Exception {
        assertEquals(1,brokerLogin.connect("TestId", "TestPassword", "Real"));
        assertEquals(1,brokerLogin.disconnect());
    }

}
