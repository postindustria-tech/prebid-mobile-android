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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.testutils.BaseSetup;
import org.prebid.mobile.testutils.Utils;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = BaseSetup.testSDK)
public class CacheServerTest {

    @After
    public void tearDown() throws IOException {
        CacheServer.getInstance().stop();
    }

    @Test
    public void testCacheServerCacheMiss() throws Exception {
        try {
            CacheServer.getInstance().start(CacheServer.DEFAULT_PORT);
        } catch (IOException e) {
            fail("Failed to start server");
        }
        URL url = new URL(String.format("http://localhost:%d/testId", CacheServer.DEFAULT_PORT));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();
        assertEquals(404, responseCode);
    }

    @Test
    public void testCacheServerCacheResponse() throws Exception {
        try {
            CacheServer.getInstance().start(CacheServer.DEFAULT_PORT);
        } catch (IOException e) {
            fail("Failed to start server");
        }
        String cacheData = UUID.randomUUID().toString();
        CacheServer.getInstance().addCache("testId", cacheData);
        URL url = new URL(String.format("http://localhost:%d/testId", CacheServer.DEFAULT_PORT));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode);
        assertEquals(cacheData, Utils.readInputStream(connection.getInputStream()));
    }
}
