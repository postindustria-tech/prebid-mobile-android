/*
 *    Copyright 2018-2019 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile;

import org.prebid.mobile.core.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

class CacheServer {

    public static final int DEFAULT_PORT = 16257;

    private static CacheServer cacheServer;
    private final ExecutorService threadPool;
    private ServerSocket socket;
    private Thread socketThread;
    private HashMap<String, String> cache = new HashMap<>();

    private CacheServer() {
        this.threadPool = Executors.newCachedThreadPool();
        this.socketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.setSoTimeout(5000);
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Socket clientSocket = socket.accept();
                            LogUtil.d("Got new connection");
                            threadPool.execute(new CacheServerRequestHandler(clientSocket, cache));
                        } catch (SocketTimeoutException e) {
                            // Ignore socket timeout
                        }
                    }
                    LogUtil.d("Received cache server interrupt. Closing...");
                    socket.close();
                } catch (IOException e) {
                    LogUtil.d(e.getMessage());
                }
            }
        });
    }

    /**
     * @return Returns {@link CacheServer} instance
     */
    static CacheServer getInstance() {
        if (cacheServer == null) {
            cacheServer = new CacheServer();
        }
        return cacheServer;
    }

    private static SSLServerSocketFactory makeSSLSocketFactory(int keystoreResource, char[] passphrase) throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream keystoreStream = PrebidMobile.getApplicationContext().getResources().openRawResource(keystoreResource);

            keystore.load(keystoreStream, passphrase);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, passphrase);
            return makeSSLSocketFactory(keystore, keyManagerFactory);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    private static SSLServerSocketFactory makeSSLSocketFactory(KeyStore loadedKeyStore, KeyManagerFactory loadedKeyFactory) throws IOException {
        try {
            return makeSSLSocketFactory(loadedKeyStore, loadedKeyFactory.getKeyManagers());
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    private static SSLServerSocketFactory makeSSLSocketFactory(KeyStore loadedKeyStore, KeyManager[] keyManagers) throws IOException {
        SSLServerSocketFactory res = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(loadedKeyStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers, trustManagerFactory.getTrustManagers(), null);
            res = ctx.getServerSocketFactory();
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        return res;
    }

    /**
     * Stops {@link CacheServer}
     *
     * @throws IOException when server failed to stop
     */
    void stop() throws IOException {
        if (cacheServer != null) {
            cacheServer.socketThread.interrupt();
            if (cacheServer.socket != null) {
                cacheServer.socket.close();
            }
            cacheServer.threadPool.shutdown();
            cacheServer = null;
        }
    }

    /**
     * Adds cache to cache server
     *
     * @param cacheId    cache id
     * @param cacheValue cache value
     */
    void addCache(String cacheId, String cacheValue) {
        cache.put(cacheId, cacheValue);
    }

    /**
     * Starts server with DEFAULT_PORT
     *
     * @throws IOException when server fails to start
     */
    void start() throws IOException {
        this.start(DEFAULT_PORT);
    }

    /**
     * Starts server with custom port
     *
     * @param port port to listen
     * @throws IOException when server fails to start
     */
    void start(int port) throws IOException {
        this.socket = makeSSLSocketFactory(R.raw.keystore, "pi12345".toCharArray()).createServerSocket(port);
        this.socketThread.start();
    }
}
