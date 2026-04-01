/*
 * Copyright (c) 2023-2024 jwdeveloper jacekwoln@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.jwdeveloper.tiktok;

import io.github.jwdeveloper.tiktok.data.requests.LiveUserData;
import io.github.jwdeveloper.tiktok.data.settings.LiveClientSettings;
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveException;
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveOfflineHostException;
import io.github.jwdeveloper.tiktok.http.LiveHttpClient;
import io.github.jwdeveloper.tiktok.listener.ListenersManager;
import io.github.jwdeveloper.tiktok.live.GiftsManager;
import io.github.jwdeveloper.tiktok.live.LiveEventsHandler;
import io.github.jwdeveloper.tiktok.live.LiveMessagesHandler;
import io.github.jwdeveloper.tiktok.websocket.LiveClientStopType;
import io.github.jwdeveloper.tiktok.websocket.LiveSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TikTokLiveClientStopTest {

    @Mock
    private LiveMessagesHandler messageHandler;
    @Mock
    private GiftsManager giftManager;
    @Mock
    private LiveHttpClient httpClient;
    @Mock
    private LiveSocketClient webSocketClient;
    @Mock
    private LiveEventsHandler eventHandler;
    @Mock
    private LiveClientSettings clientSettings;
    @Mock
    private ListenersManager listenersManager;

    private final Logger logger = Logger.getLogger(TikTokLiveClientStopTest.class.getName());
    private TikTokRoomInfo roomInfo;
    private TikTokLiveClient client;

    @BeforeEach
    void setUp() {
        roomInfo = new TikTokRoomInfo();
        roomInfo.setHostName("user");
        lenient().when(clientSettings.isUseEulerstreamWebsocket()).thenReturn(false);
        lenient().when(webSocketClient.isConnected()).thenReturn(false);
        client = new TikTokLiveClient(messageHandler, giftManager, roomInfo, httpClient, webSocketClient,
                eventHandler, clientSettings, listenersManager, logger);
    }

    @Test
    void stop_disconnectsClearsListenersAndShutsDownManager() {
        when(webSocketClient.isConnected()).thenReturn(true);
        Object l1 = new Object();
        when(listenersManager.getListeners()).thenReturn(List.of(l1));

        client.stop();

        verify(webSocketClient).stop(LiveClientStopType.DISCONNECT);
        verify(listenersManager).removeListener(l1);
        verify(eventHandler).clearSubscriptions();
        verify(listenersManager).shutdown();
    }

    @Test
    void stop_secondCallIsIdempotent() {
        when(webSocketClient.isConnected()).thenReturn(true);

        client.stop();
        client.stop();

        verify(webSocketClient, times(1)).stop(LiveClientStopType.DISCONNECT);
    }

    @Test
    void connect_afterStop_throws() {
        client.stop();
        assertThrows(TikTokLiveException.class, () -> client.connect());
    }

    @Test
    void stop_cancelsScheduledReconnect() throws InterruptedException {
        when(clientSettings.isRetryOnConnectionFailure()).thenReturn(true);
        when(clientSettings.getRetryConnectionTimeout()).thenReturn(Duration.ofMillis(80));
        var offline = new LiveUserData.Response("{}", LiveUserData.UserStatus.Offline, roomInfo);
        when(httpClient.fetchLiveUserData(isA(LiveUserData.Request.class))).thenReturn(offline);

        assertThrows(TikTokLiveOfflineHostException.class, () -> client.connect());
        client.stop();
        Thread.sleep(250);
        verify(httpClient, times(1)).fetchLiveUserData(isA(LiveUserData.Request.class));
    }

    @Test
    void retryRunsSecondConnect_whenNotStopped() throws InterruptedException {
        when(clientSettings.isRetryOnConnectionFailure()).thenReturn(true);
        when(clientSettings.getRetryConnectionTimeout()).thenReturn(Duration.ofMillis(80));
        var offline = new LiveUserData.Response("{}", LiveUserData.UserStatus.Offline, roomInfo);
        when(httpClient.fetchLiveUserData(isA(LiveUserData.Request.class))).thenReturn(offline);

        assertThrows(TikTokLiveOfflineHostException.class, () -> client.connect());
        Thread.sleep(250);
        verify(httpClient, atLeast(2)).fetchLiveUserData(isA(LiveUserData.Request.class));
        client.stop();
    }
}
