/** 
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements. See the NOTICE file 
 * distributed with this work for additional information 
 * regarding copyright ownership. The ASF licenses this file 
 * to you under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance 
 * with the License. You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied. See the License for the 
 * specific language governing permissions and limitations 
 * under the License. 
 */
package org.apache.cxf.dosgi.discovery.zookeeper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.discovery.DiscoveredServiceNotification;
import org.osgi.service.discovery.DiscoveredServiceTracker;

public class FindInZooKeeperCustomizerTest extends TestCase {
    public void testAddingService() {
        DiscoveredServiceTracker dst = new DiscoveredServiceTracker() {
            public void serviceChanged(DiscoveredServiceNotification dsn) {
                // TODO are we expecting something here?
                // maybe a separate test?
            }            
        };
        
        ServiceReference sr = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr.getProperty(DiscoveredServiceTracker.INTERFACE_MATCH_CRITERIA)).
            andReturn(Collections.singleton(String.class.getName()));
        EasyMock.replay(sr);
        
        DiscoveredServiceTracker dst2 = new DiscoveredServiceTracker() {
            public void serviceChanged(DiscoveredServiceNotification dsn) {
                // TODO are we expecting something here?
                // maybe a separate test?
            }            
        };
        
        ServiceReference sr2 = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr2.getProperty(DiscoveredServiceTracker.INTERFACE_MATCH_CRITERIA)).
            andReturn(Arrays.asList(Integer.class.getName(), Comparable.class.getName()));
        EasyMock.replay(sr2);
        
        BundleContext bc = EasyMock.createMock(BundleContext.class);
        EasyMock.expect(bc.getService(sr)).andReturn(dst);
        EasyMock.expect(bc.getService(sr2)).andReturn(dst2);
        EasyMock.replay(bc);
        
        ZooKeeper zk = EasyMock.createStrictMock(ZooKeeper.class);
        zkExpectExists(zk, String.class.getName());
        zkExpectExists(zk, Integer.class.getName());
        zkExpectExists(zk, Comparable.class.getName());
        EasyMock.expectLastCall();
        EasyMock.replay(zk);
        
        FindInZooKeeperCustomizer fc = new FindInZooKeeperCustomizer(bc, zk);

        // ---------------------------------------------------------------
        // Test the addingService APIs
        // ---------------------------------------------------------------
        
        assertEquals("Precondition failed", 0, fc.watchers.size());
        fc.addingService(sr);
        assertEquals(1, fc.watchers.size());
        
        DiscoveredServiceTracker key = fc.watchers.keySet().iterator().next();
        assertSame(dst, key);
        List<InterfaceMonitor> dmList = fc.watchers.get(key);
        assertEquals(1, dmList.size());
        InterfaceMonitor dm = dmList.iterator().next();
        assertNotNull(dm.listener);
        assertSame(zk, dm.zookeeper);
        assertEquals(Util.getZooKeeperPath(String.class.getName()), dm.znode);        

        assertEquals("Precondition failed", 1, fc.watchers.size());
        fc.addingService(sr2);
        assertEquals(2, fc.watchers.size());
        
        assertTrue(fc.watchers.containsKey(dst));
        assertTrue(fc.watchers.containsKey(dst2));
        assertEquals(dmList, fc.watchers.get(dst));
        List<InterfaceMonitor> dmList2 = fc.watchers.get(dst2);
        assertEquals(2, dmList2.size());
        assertEquals(Util.getZooKeeperPath(Integer.class.getName()), dmList2.get(0).znode);
        assertEquals(Util.getZooKeeperPath(Comparable.class.getName()), dmList2.get(1).znode);
        
        EasyMock.verify(zk);

        // ---------------------------------------------------------------
        // Test the modifiedService APIs
        // ---------------------------------------------------------------
        EasyMock.reset(zk);
        zkExpectExists(zk, List.class.getName());
        EasyMock.replay(zk);
        
        EasyMock.reset(sr);
        EasyMock.expect(sr.getProperty(DiscoveredServiceTracker.INTERFACE_MATCH_CRITERIA)).
            andReturn(Collections.singleton(List.class.getName()));
        EasyMock.replay(sr);
        
        assertEquals("Precondition failed", 2, fc.watchers.size());
        fc.modifiedService(sr, dst);
        assertEquals("Precondition failed", 2, fc.watchers.size());
        
        assertTrue(fc.watchers.containsKey(dst));
        assertTrue(fc.watchers.containsKey(dst2));
        assertEquals(dmList2, fc.watchers.get(dst2));
        List<InterfaceMonitor> dmList3 = fc.watchers.get(dst);
        assertEquals(1, dmList3.size());
        assertEquals(Util.getZooKeeperPath(List.class.getName()), dmList3.iterator().next().znode);

        EasyMock.verify(zk);       

        // ---------------------------------------------------------------
        // Test the removedService APIs
        // ---------------------------------------------------------------
        EasyMock.reset(zk);
        EasyMock.replay(zk);
        
        assertEquals("Precondition failed", 2, fc.watchers.size());
        fc.removedService(sr2, dst2);
        assertEquals("Precondition failed", 1, fc.watchers.size());
        
        assertEquals(dmList3, fc.watchers.get(dst));
        assertNull(fc.watchers.get(dst2));
        
        EasyMock.verify(zk);       
    }

    private void zkExpectExists(ZooKeeper zk, String className) {
        zk.exists(EasyMock.eq(Util.getZooKeeperPath(className)), 
                (Watcher) EasyMock.anyObject(), 
                (StatCallback) EasyMock.anyObject(), EasyMock.isNull());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                assertEquals(EasyMock.getCurrentArguments()[1],
                        EasyMock.getCurrentArguments()[2]);
                return null;
            }            
        });
    }
}