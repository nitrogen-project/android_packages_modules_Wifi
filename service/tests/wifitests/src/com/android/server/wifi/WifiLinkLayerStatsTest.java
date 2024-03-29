/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.net.MacAddress;
import android.net.wifi.MloLink;
import android.net.wifi.WifiScanner;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Unit tests for {@link com.android.server.wifi.WifiLinkLayerStats}.
 */
@SmallTest
public class WifiLinkLayerStatsTest extends WifiBaseTest {

    private static final String WIFI_IFACE_NAME = "wlanTest";

    ExtendedWifiInfo mWifiInfo;
    WifiLinkLayerStats mWifiLinkLayerStats;
    Random mRandom = new Random();

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        mWifiInfo = new ExtendedWifiInfo(mock(WifiGlobals.class), WIFI_IFACE_NAME);
        mWifiLinkLayerStats = new WifiLinkLayerStats();
    }

    private void setupLinkStats() {
        if (mWifiLinkLayerStats == null) return;
        final int numLinks = 2;
        int[] freqs = {2412, 5652};

        mWifiLinkLayerStats.links = new WifiLinkLayerStats.LinkSpecificStats[numLinks];
        for (int i = 0; i < numLinks; i++) {
            mWifiLinkLayerStats.links[i] = new WifiLinkLayerStats.LinkSpecificStats();
            mWifiLinkLayerStats.links[i].link_id = i + 1;
            mWifiLinkLayerStats.links[i].radio_id = mRandom.nextInt(5);
            mWifiLinkLayerStats.links[i].rssi_mgmt = mRandom.nextInt(127);
            mWifiLinkLayerStats.links[i].beacon_rx = mRandom.nextInt(1000);
            mWifiLinkLayerStats.links[i].frequencyMhz = freqs[i];
        }
    }

    private void setupMloLinks() {
        List<MloLink> mloLinks = new ArrayList<>();
        MloLink link1 = new MloLink();
        link1.setBand(WifiScanner.WIFI_BAND_24_GHZ);
        link1.setChannel(6);
        link1.setApMacAddress(MacAddress.fromString("01:02:03:04:05:06"));
        link1.setLinkId(1);
        MloLink link2 = new MloLink();
        link2.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link2.setChannel(44);
        link2.setApMacAddress(MacAddress.fromString("01:02:03:04:05:07"));
        link2.setLinkId(2);
        mloLinks.add(link1);
        mloLinks.add(link2);

        mWifiInfo.setAffiliatedMloLinks(mloLinks);
    }

    /**
     * Increments the counters
     *
     * The values are carved up among the 4 classes (be, bk, vi, vo) so the totals come out right.
     */
    private void bumpCounters(WifiLinkLayerStats s, int txg, int txr, int txb, int rxg) {
        int a = mRandom.nextInt(31);
        int b = mRandom.nextInt(31);
        int m0 = a & b;
        int m1 = a & ~b;
        int m2 = ~a & b;
        int m3 = ~a & ~b;
        assertEquals(-1, m0 + m1 + m2 + m3);

        s.rxmpdu_be += rxg & m0;
        s.txmpdu_be += txg & m0;
        s.lostmpdu_be += txb & m0;
        s.retries_be += txr & m0;

        s.rxmpdu_bk += rxg & m1;
        s.txmpdu_bk += txg & m1;
        s.lostmpdu_bk += txb & m1;
        s.retries_bk += txr & m1;

        s.rxmpdu_vi += rxg & m2;
        s.txmpdu_vi += txg & m2;
        s.lostmpdu_vi += txb & m2;
        s.retries_vi += txr & m2;

        s.rxmpdu_vo += rxg & m3;
        s.txmpdu_vo += txg & m3;
        s.lostmpdu_vo += txb & m3;
        s.retries_vo += txr & m3;

        if (s.links == null) return;

        for (WifiLinkLayerStats.LinkSpecificStats l : s.links) {
            l.rxmpdu_be += rxg & m0;
            l.txmpdu_be += txg & m0;
            l.lostmpdu_be += txb & m0;
            l.retries_be += txr & m0;

            l.rxmpdu_bk += rxg & m1;
            l.txmpdu_bk += txg & m1;
            l.lostmpdu_bk += txb & m1;
            l.retries_bk += txr & m1;

            l.rxmpdu_vi += rxg & m2;
            l.txmpdu_vi += txg & m2;
            l.lostmpdu_vi += txb & m2;
            l.retries_vi += txr & m2;

            l.rxmpdu_vo += rxg & m3;
            l.txmpdu_vo += txg & m3;
            l.lostmpdu_vo += txb & m3;
            l.retries_vo += txr & m3;
        }
    }

    /**
     *
     * Check that average rates converge to the right values
     *
     * Check that the total packet counts are correct
     *
     */
    @Test
    public void checkThatAverageRatesConvergeToTheRightValuesAndTotalsAreRight() throws Exception {
        int txg = mRandom.nextInt(1000);
        int txr = mRandom.nextInt(100);
        int txb = mRandom.nextInt(100);
        int rxg = mRandom.nextInt(1000);
        setupLinkStats();
        setupMloLinks();
        assertNotNull(mWifiInfo.getAffiliatedMloLinks());
        assertNotNull(mWifiLinkLayerStats.links);

        int n = 3 * 5; // Time constant is 3 seconds, 5 times time constant should get 99% there
        for (int i = 0; i < n; i++) {
            bumpCounters(mWifiLinkLayerStats, txg, txr, txb, rxg);
            mWifiLinkLayerStats.timeStampInMs += 1000;
            mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        }
        // assertEquals(double, double, double) takes a tolerance as the third argument
        assertEquals((double) txg, mWifiInfo.getSuccessfulTxPacketsPerSecond(), txg * 0.02);
        assertEquals((double) txr, mWifiInfo.getRetriedTxPacketsPerSecond(), txr * 0.02);
        assertEquals((double) txb, mWifiInfo.getLostTxPacketsPerSecond(), txb * 0.02);
        assertEquals((double) rxg, mWifiInfo.getSuccessfulRxPacketsPerSecond(), rxg * 0.02);

        assertEquals(mWifiInfo.txSuccess, n * txg);
        assertEquals(mWifiInfo.txRetries, n * txr);
        assertEquals(mWifiInfo.txBad, n * txb);
        assertEquals(mWifiInfo.rxSuccess, n * rxg);

        for (MloLink mloLink : mWifiInfo.getAffiliatedMloLinks()) {
            assertEquals((double) txg, mloLink.getSuccessfulTxPacketsPerSecond(), txg * 0.02);
            assertEquals((double) txr, mloLink.getRetriedTxPacketsPerSecond(), txr * 0.02);
            assertEquals((double) txb, mloLink.getLostTxPacketsPerSecond(), txb * 0.02);
            assertEquals((double) rxg, mloLink.getSuccessfulRxPacketsPerSecond(), rxg * 0.02);

            assertEquals(mloLink.txSuccess, n * ((long) txg));
            assertEquals(mloLink.txRetries, n * ((long) txr));
            assertEquals(mloLink.txBad, n * ((long) txb));
            assertEquals(mloLink.rxSuccess, n * ((long) rxg));
        }
    }

    /**
     * A single packet in a short period of time should have small effect
     */
    @Test
    public void aSinglePacketInAShortPeriodOfTimeShouldHaveSmallEffect() throws Exception {
        bumpCounters(mWifiLinkLayerStats, 999999999, 999999999, 999999999, 99999999);
        mWifiLinkLayerStats.timeStampInMs = 999999999;
        mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        assertEquals(0.0, mWifiInfo.getSuccessfulTxPacketsPerSecond(), 0.0001);
        bumpCounters(mWifiLinkLayerStats, 1, 1, 1, 1);
        mWifiLinkLayerStats.timeStampInMs += 1;
        mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        assertEquals(0.33, mWifiInfo.getSuccessfulTxPacketsPerSecond(), 0.01);
    }

    /**
     * Check for bad interactions with the alternative updatePacketRates method
     */
    @Test
    public void afterSourceSwitchTheRatesShouldGetReset() throws Exception {
        // Do some updates using link layer stats
        bumpCounters(mWifiLinkLayerStats, 999, 999, 999, 999);
        mWifiLinkLayerStats.timeStampInMs = 999999999;
        mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        assertEquals(0.0, mWifiInfo.getSuccessfulTxPacketsPerSecond(), 0.0001);
        assertEquals(0.0, mWifiInfo.getSuccessfulRxPacketsPerSecond(), 0.0001);
        bumpCounters(mWifiLinkLayerStats, 1_000_000_000, 777000, 66600, 1_000_100_000);
        mWifiLinkLayerStats.timeStampInMs += 10_000;
        mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        assertTrue("" + mWifiInfo + " " + mWifiLinkLayerStats,
                mWifiInfo.getSuccessfulTxPacketsPerSecond() > 0.95e+8);
        assertTrue("" + mWifiInfo + " " + mWifiLinkLayerStats,
                mWifiInfo.getSuccessfulRxPacketsPerSecond() > 0.95e+8);
        // Now update with traffic counters
        mWifiLinkLayerStats.timeStampInMs += 10_000;
        mWifiInfo.updatePacketRates(2_000_000_000L, 2_000_000_000L,
                mWifiLinkLayerStats.timeStampInMs);
        // Despite the increase, the rates should be zero after the change in source
        assertEquals(0.0, mWifiInfo.getSuccessfulTxPacketsPerSecond(), 0.0001);
        assertEquals(0.0, mWifiInfo.getSuccessfulRxPacketsPerSecond(), 0.0001);
        assertEquals(0, mWifiInfo.txBad);
        assertEquals(0, mWifiInfo.txRetries);
        // Make sure that updates from this source work, too
        mWifiLinkLayerStats.timeStampInMs += 10_000;
        mWifiInfo.updatePacketRates(3_000_000_000L, 3_000_000_000L,
                mWifiLinkLayerStats.timeStampInMs);
        assertTrue(mWifiInfo.getSuccessfulTxPacketsPerSecond() > 0.95e+8);
        assertTrue(mWifiInfo.getSuccessfulRxPacketsPerSecond() > 0.95e+8);
        // Switch back to using link layer stats
        mWifiLinkLayerStats.timeStampInMs += 10_000;
        bumpCounters(mWifiLinkLayerStats, 1_000_000_000, 777000, 66600, 1_000_100_000);
        mWifiInfo.updatePacketRates(mWifiLinkLayerStats, mWifiLinkLayerStats.timeStampInMs);
        assertEquals(0.0, mWifiInfo.getSuccessfulTxPacketsPerSecond(), 0.0001);
        assertEquals(0.0, mWifiInfo.getSuccessfulRxPacketsPerSecond(), 0.0001);
    }
}
