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
package com.rapleaf.hank.coordinator;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MockHost extends AbstractHost {
  private final PartitionServerAddress address;
  private HostState state = HostState.OFFLINE;
  private List<HostCommand> commandQueue = new LinkedList<HostCommand>();
  private HostCommand currentCommand;
  private final Set<HostCommandQueueChangeListener> commandQueueChangeListeners = new HashSet<HostCommandQueueChangeListener>();
  private final Set<HostCurrentCommandChangeListener> currentCommandChangeListeners = new HashSet<HostCurrentCommandChangeListener>();

  public MockHost(PartitionServerAddress address) {
    this.address = address;
  }

  @Override
  public HostDomain addDomain(Domain domain) throws IOException {
    return null;
  }

  @Override
  public PartitionServerAddress getAddress() {
    return address;
  }

  @Override
  public Set<HostDomain> getAssignedDomains() throws IOException {
    return null;
  }

  @Override
  public HostState getState() throws IOException {
    return state;
  }

  @Override
  public void setStateChangeListener(HostStateChangeListener listener) {
  }

  @Override
  public HostCommand getCurrentCommand() throws IOException {
    return currentCommand;
  }

  private void setCurrentCommand(HostCommand command) {
    currentCommand = command;
    notifyCurrentCommandChangeListeners();
  }

  @Override
  public void setState(HostState state) throws IOException {
    this.state = state;
  }

  @Override
  public void enqueueCommand(HostCommand command) throws IOException {
    commandQueue.add(command);
    notifyCommandQueueChangeListeners();
  }

  @Override
  public List<HostCommand> getCommandQueue() throws IOException {
    return commandQueue;
  }

  @Override
  public HostCommand nextCommand() throws IOException {
    if (commandQueue.size() > 0) {
      setCurrentCommand(commandQueue.remove(0));
      notifyCommandQueueChangeListeners();
    } else {
      setCurrentCommand(null);
    }
    return currentCommand;
  }

  @Override
  public void setCommandQueueChangeListener(HostCommandQueueChangeListener listener) throws IOException {
    commandQueueChangeListeners.add(listener);
  }

  protected void notifyCommandQueueChangeListeners() {
    for (HostCommandQueueChangeListener listener : commandQueueChangeListeners) {
      listener.onCommandQueueChange(this);
    }
  }

  @Override
  public void setCurrentCommandChangeListener(HostCurrentCommandChangeListener listener) throws IOException {
    currentCommandChangeListeners.add(listener);
  }

  protected void notifyCurrentCommandChangeListeners() {
    for (HostCurrentCommandChangeListener listener : currentCommandChangeListeners) {
      listener.onCurrentCommandChange(MockHost.this);
    }
  }

  @Override
  public void cancelStateChangeListener(HostStateChangeListener listener) {
  }

  @Override
  public void clearCommandQueue() throws IOException {
    commandQueue.clear();
    notifyCommandQueueChangeListeners();
  }
}
