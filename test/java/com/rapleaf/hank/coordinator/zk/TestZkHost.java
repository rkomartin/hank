/**
 *  Copyright 2011 Rapleaf
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.rapleaf.hank.coordinator.zk;

import com.rapleaf.hank.ZkTestCase;
import com.rapleaf.hank.coordinator.*;
import com.rapleaf.hank.coordinator.mock.MockCoordinator;
import com.rapleaf.hank.coordinator.mock.MockDomain;
import com.rapleaf.hank.util.Condition;
import com.rapleaf.hank.util.WaitUntil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class TestZkHost extends ZkTestCase {

  private static final PartitionServerAddress ADDRESS = new PartitionServerAddress("my.super.host", 32267);

  private Coordinator coordinator;
  private Domain d0 = new MockDomain("d0");

  @Override
  public void setUp() throws Exception {
    super.setUp();
    coordinator = new MockCoordinator() {
      @Override
      public Domain getDomainById(int domainId) {
        if (domainId == 0) {
          return d0;
        } else {
          throw new IllegalStateException();
        }
      }
    };
  }

  public void testCreateAndLoad() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    assertEquals(ADDRESS, host.getAddress());
    assertEquals(0, host.getCommandQueue().size());
    assertNull(host.getCurrentCommand());
    assertEquals(HostState.OFFLINE, host.getState());
    assertFalse(Hosts.isOnline(host));

    host.setEphemeralStatistic("a", "A");
    host.setEphemeralStatistic("b", "B");
    Thread.sleep(10);
    assertEquals("A", host.getStatistic("a"));
    assertEquals("B", host.getStatistic("b"));
    assertNull(host.getStatistic("c"));
  }

  public void testStateChangeListener() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    MockHostStateChangeListener mockListener = new MockHostStateChangeListener();
    host.setStateChangeListener(mockListener);

    synchronized (mockListener) {
      mockListener.wait(100);
    }

    assertNull("should not receive a callback until something is changed...",
        mockListener.calledWith);

    host.setState(HostState.SERVING);
    synchronized (mockListener) {
      mockListener.wait(1000);
    }
    assertNotNull("mock listener should have received a call!", mockListener.calledWith);
    assertEquals(HostState.SERVING, mockListener.calledWith);
    host.close();
  }

  public void testSetState() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    assertEquals(HostState.OFFLINE, host.getState());
    assertFalse(Hosts.isOnline(host));

    host.setState(HostState.IDLE);
    Thread.sleep(100);
    assertEquals(HostState.IDLE, host.getState());
    assertTrue(Hosts.isOnline(host));

    host.setState(HostState.OFFLINE);
    Thread.sleep(100);
    assertEquals(HostState.OFFLINE, host.getState());
    assertFalse(Hosts.isOnline(host));
    host.close();
  }

  public void testCommandQueue() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    assertEquals(Collections.EMPTY_LIST, host.getCommandQueue());
    assertNull(host.getCurrentCommand());

    host.enqueueCommand(HostCommand.GO_TO_IDLE);
    assertEquals(Arrays.asList(HostCommand.GO_TO_IDLE), host.getCommandQueue());
    assertNull(host.getCurrentCommand());

    host.enqueueCommand(HostCommand.SERVE_DATA);
    assertEquals(Arrays.asList(HostCommand.GO_TO_IDLE, HostCommand.SERVE_DATA), host.getCommandQueue());
    assertNull(host.getCurrentCommand());

    assertEquals(HostCommand.GO_TO_IDLE, host.nextCommand());
    Thread.sleep(10);
    assertEquals(HostCommand.GO_TO_IDLE, host.getCurrentCommand());
    assertEquals(Arrays.asList(HostCommand.SERVE_DATA), host.getCommandQueue());

    assertEquals(Arrays.asList(HostCommand.SERVE_DATA), host.getCommandQueue());

    host.clearCommandQueue();
    assertEquals(Collections.EMPTY_LIST, host.getCommandQueue());

    host.close();
  }

  public void testCommandQueueListener() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    MockHostCommandQueueChangeListener l2 = new MockHostCommandQueueChangeListener();
    host.setCommandQueueChangeListener(l2);
    MockHostStateChangeListener l1 = new MockHostStateChangeListener();
    host.setStateChangeListener(l1);

    assertNull(l1.calledWith);
    assertNull(l2.calledWith);

    host.enqueueCommand(HostCommand.EXECUTE_UPDATE);
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.enqueueCommand(HostCommand.SERVE_DATA);
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.enqueueCommand(HostCommand.GO_TO_IDLE);
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.nextCommand();
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.nextCommand();
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.nextCommand();
    l2.waitForNotification();
    assertNull(l1.calledWith);
    assertEquals(host, l2.calledWith);
    l2.calledWith = null;

    host.nextCommand();
    l2.waitForNotification(true);
    assertNull(l1.calledWith);
    assertNull(l2.calledWith);
  }

  public void testDomains() throws Exception {
    final ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    assertEquals(0, host.getAssignedDomains().size());

    host.addDomain(d0);
    WaitUntil.condition(new Condition() {
      @Override
      public boolean test() {
        try {
          return !host.getAssignedDomains().isEmpty();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    HostDomain hostDomain = (HostDomain) host.getAssignedDomains().toArray()[0];
    assertEquals(0, hostDomain.getDomain().getId());
    assertEquals(0, host.getHostDomain(d0).getDomain().getId());
  }

  public void testUptime() throws Exception {
    ZkHost host = ZkHost.create(getZk(), coordinator, getRoot(), ADDRESS, null, Collections.<String>emptyList());
    assertNull(host.getUpSince());
    final long currentTimeMillis = System.currentTimeMillis();
    host.setState(HostState.IDLE);
    dumpZk();
    Thread.sleep(2000);
    assertNotNull(host.getUpSince());
    assertTrue(host.getUpSince() >= currentTimeMillis);
  }
}
