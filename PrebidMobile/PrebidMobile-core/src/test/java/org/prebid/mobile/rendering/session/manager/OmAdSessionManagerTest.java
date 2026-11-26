/*
 *    Copyright 2018-2021 Prebid.org, Inc.
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

package org.prebid.mobile.rendering.session.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.rendering.video.VideoAdEvent;
import org.prebid.mobile.rendering.video.vast.AdVerifications;
import org.prebid.mobile.rendering.video.vast.Verification;
import org.prebid.mobile.test.utils.WhiteBox;
import com.iab.omid.library.prebidorg.adsession.VerificationScriptResource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 19)
public class OmAdSessionManagerTest {

    private static final String SCRIPT_URL = "https://verification.example.com/omid.js";
    private static final String OTHER_SCRIPT_URL = "https://other.example.com/omid.js";

    private final List<LogEntry> logs = new ArrayList<>();
    private int previousLogLevel;

    private OmAdSessionManager omAdSessionManager;

    @Before
    public void setUp() throws Exception {
        previousLogLevel = LogUtil.getLogLevel();
        LogUtil.setLogLevel(LogUtil.VERBOSE);
        LogUtil.setLogger(new LogUtil.PrebidLogger() {
            @Override
            public void println(int messagePriority, String tag, String message) {
                logs.add(new LogEntry(messagePriority, message));
            }

            @Override
            public void e(String tag, String message, Throwable throwable) {
                logs.add(new LogEntry(LogUtil.ERROR, message));
            }
        });

        // createNewInstance() requires an activated OM SDK; the paths under test do not use the JS library.
        Constructor<OmAdSessionManager> constructor =
            OmAdSessionManager.class.getDeclaredConstructor(org.prebid.mobile.rendering.sdk.JSLibraryManager.class);
        constructor.setAccessible(true);
        omAdSessionManager = constructor.newInstance((Object) null);
    }

    @After
    public void tearDown() {
        LogUtil.setLogLevel(previousLogLevel);
        LogUtil.setLogger(new LogUtil.PrebidLogger() {
            @Override
            public void println(int messagePriority, String tag, String message) {
                android.util.Log.println(messagePriority, tag, message);
            }

            @Override
            public void e(String tag, String message, Throwable throwable) {
                android.util.Log.e(tag, message, throwable);
            }
        });
    }

    // region createVerificationScriptResources

    @Test
    public void createVerificationScriptResources_withVendorAndParameters_keepsBoth() throws Exception {
        List<VerificationScriptResource> resources = createResources(
            verification("doubleverify.com-omid", SCRIPT_URL, "ctx=1&cmp=2")
        );

        assertEquals(1, resources.size());
        VerificationScriptResource resource = resources.get(0);
        assertEquals(SCRIPT_URL, resource.getResourceUrl().toString());
        assertEquals("doubleverify.com-omid", resource.getVendorKey());
        assertEquals("ctx=1&cmp=2", resource.getVerificationParameters());
    }

    @Test
    public void createVerificationScriptResources_withoutParameters_createsResourceWithoutParameters() throws Exception {
        List<VerificationScriptResource> resources = createResources(
            verification("doubleverify.com-omid", SCRIPT_URL, null),
            verification("ias.com-omid", OTHER_SCRIPT_URL, "")
        );

        assertEquals(2, resources.size());
        assertEquals(SCRIPT_URL, resources.get(0).getResourceUrl().toString());
        assertNull(resources.get(0).getVerificationParameters());
        assertEquals(OTHER_SCRIPT_URL, resources.get(1).getResourceUrl().toString());
        assertNull(resources.get(1).getVerificationParameters());
        assertNoErrors();
    }

    @Test
    public void createVerificationScriptResources_withoutVendor_createsResourceWithoutParameters() throws Exception {
        List<VerificationScriptResource> resources = createResources(
            verification(null, SCRIPT_URL, "ctx=1")
        );

        assertEquals(1, resources.size());
        assertEquals(SCRIPT_URL, resources.get(0).getResourceUrl().toString());
        assertNull(resources.get(0).getVendorKey());
        assertNull(resources.get(0).getVerificationParameters());
    }

    @Test
    public void createVerificationScriptResources_malformedUrl_skipsOnlyThatScript() throws Exception {
        List<VerificationScriptResource> resources = createResources(
            verification("broken.com-omid", "not a url", "p=1"),
            verification("doubleverify.com-omid", SCRIPT_URL, "ctx=1"),
            verification("missing.com-omid", null, "p=2")
        );

        assertEquals(1, resources.size());
        assertEquals(SCRIPT_URL, resources.get(0).getResourceUrl().toString());
        assertEquals("doubleverify.com-omid", resources.get(0).getVendorKey());
        assertEquals(2, countLogs(LogUtil.ERROR, "Skipping OM verification script"));
    }

    @Test
    public void createVerificationScriptResources_logsOnlyTheScriptsThatAreUsed() throws Exception {
        createResources(
            verification("broken.com-omid", "not a url", "p=1"),
            verification("doubleverify.com-omid", SCRIPT_URL, "ctx=1")
        );

        assertEquals(1, countLogs(LogUtil.DEBUG, "Using jsResource"));
        assertEquals(1, countLogs(LogUtil.DEBUG, "Using jsResource: " + SCRIPT_URL));
    }

    @Test
    public void createVerificationScriptResources_allInvalid_returnsEmptyList() throws Exception {
        List<VerificationScriptResource> resources = createResources(
            verification("broken.com-omid", "not a url", "p=1")
        );

        assertTrue(resources.isEmpty());
    }

    @Test
    public void createVerificationScriptResources_nullInput_returnsNull() throws Exception {
        assertNull(invokeCreateResources(null));
        assertNull(invokeCreateResources(new AdVerifications((ArrayList<Verification>) null)));
    }

    // endregion

    // region no verification scripts

    @Test
    public void initVideoAdSession_nullVerifications_skipsMeasurementWithDebugLog() throws Exception {
        omAdSessionManager.initVideoAdSession(null, null);

        assertTrue(isMeasurementSkipped());
        assertNull(WhiteBox.getInternalState(omAdSessionManager, "adSession"));
        assertEquals(1, countLogs(LogUtil.DEBUG, "No usable AdVerifications in VAST"));
        assertNoErrors();
    }

    @Test
    public void initVideoAdSession_emptyVerifications_skipsMeasurementWithDebugLog() throws Exception {
        omAdSessionManager.initVideoAdSession(new AdVerifications(new ArrayList<>()), null);

        assertTrue(isMeasurementSkipped());
        assertEquals(1, countLogs(LogUtil.DEBUG, "No usable AdVerifications in VAST"));
        assertNoErrors();
    }

    @Test
    public void initVideoAdSession_onlyUnusableScripts_skipsMeasurement() throws Exception {
        AdVerifications adVerifications = new AdVerifications(new ArrayList<>(Arrays.asList(
            verification("broken.com-omid", "not a url", "p=1"),
            verification("missing.com-omid", null, "p=2")
        )));

        omAdSessionManager.initVideoAdSession(adVerifications, null);

        assertTrue(isMeasurementSkipped());
        assertNull(WhiteBox.getInternalState(omAdSessionManager, "adSession"));
        assertEquals(1, countLogs(LogUtil.DEBUG, "No usable AdVerifications in VAST"));
        assertEquals(0, countLogs(LogUtil.DEBUG, "Using jsResource"));
    }

    @Test
    public void omEventsAfterSkippedMeasurement_logDebugNotError() {
        omAdSessionManager.initVideoAdSession(null, null);
        logs.clear();

        omAdSessionManager.startAdSession();
        omAdSessionManager.nonSkippableStandaloneVideoAdLoaded(true);
        omAdSessionManager.registerImpression();
        omAdSessionManager.videoAdStarted(15f, 1f);
        omAdSessionManager.trackAdVideoEvent(VideoAdEvent.Event.AD_MIDPOINT);
        omAdSessionManager.trackVolumeChange(0f);
        omAdSessionManager.stopAdSession();

        assertNoErrors();
        assertEquals(7, countLogs(LogUtil.DEBUG, "Open Measurement was skipped for this ad"));
    }

    @Test
    public void omEventsWithoutSessionAndWithoutSkip_stillLogError() {
        // No initVideoAdSession call: a missing session here is a real failure and must stay visible.
        omAdSessionManager.registerImpression();
        omAdSessionManager.startAdSession();

        assertEquals(1, countLogs(LogUtil.ERROR, "Failed to registerImpression"));
        assertEquals(1, countLogs(LogUtil.ERROR, "Failed to startAdSession"));
        assertEquals(0, countLogs(LogUtil.DEBUG, "Open Measurement was skipped"));
    }

    @Test
    public void initVideoAdSession_withVerificationsAfterSkippedAd_resetsSkipFlag() throws Exception {
        omAdSessionManager.initVideoAdSession(null, null);
        assertTrue(isMeasurementSkipped());

        AdVerifications adVerifications = new AdVerifications(new ArrayList<>(Arrays.asList(
            verification("doubleverify.com-omid", SCRIPT_URL, "ctx=1")
        )));
        try {
            omAdSessionManager.initVideoAdSession(adVerifications, null);
        }
        catch (RuntimeException ignored) {
            // Session creation itself needs an activated OM SDK, which unit tests do not have.
        }

        assertFalse(isMeasurementSkipped());
    }

    // endregion

    private Verification verification(String vendor, String jsResource, String parameters) {
        Verification verification = mock(Verification.class);
        when(verification.getVendor()).thenReturn(vendor);
        when(verification.getJsResource()).thenReturn(jsResource);
        when(verification.getVerificationParameters()).thenReturn(parameters);
        return verification;
    }

    private List<VerificationScriptResource> createResources(Verification... verifications) throws Exception {
        return invokeCreateResources(new AdVerifications(new ArrayList<>(Arrays.asList(verifications))));
    }

    @SuppressWarnings("unchecked")
    private List<VerificationScriptResource> invokeCreateResources(AdVerifications adVerifications) throws Exception {
        Method method = OmAdSessionManager.class.getDeclaredMethod("createVerificationScriptResources",
                                                                   AdVerifications.class);
        method.setAccessible(true);
        return (List<VerificationScriptResource>) method.invoke(omAdSessionManager, adVerifications);
    }

    private boolean isMeasurementSkipped() throws Exception {
        Boolean value = WhiteBox.getInternalState(omAdSessionManager, "measurementSkipped");
        return value != null && value;
    }

    private int countLogs(int priority, String messagePart) {
        int count = 0;
        for (LogEntry entry : logs) {
            if (entry.priority == priority && entry.message.contains(messagePart)) {
                count++;
            }
        }
        return count;
    }

    private void assertNoErrors() {
        for (LogEntry entry : logs) {
            assertFalse("Unexpected error log: " + entry.message, entry.priority >= LogUtil.ERROR);
        }
    }

    private static class LogEntry {

        final int priority;
        final String message;

        LogEntry(int priority, String message) {
            this.priority = priority;
            this.message = message;
        }
    }
}
