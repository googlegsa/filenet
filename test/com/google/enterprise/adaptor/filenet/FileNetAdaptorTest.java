// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.filenet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;

import com.filenet.api.util.UserContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.security.Principal;
import javax.security.auth.Subject;

/** Unit tests for FileNetAdaptor */
public class FileNetAdaptorTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FileNetAdaptor adaptor;
  private AdaptorContext context;
  private Config config;

  @Before
  public void setUp() throws Exception {
    FileNetProxies proxies = new FileNetProxies();
    adaptor = new FileNetAdaptor(proxies);
    context = ProxyAdaptorContext.getInstance();
    config = context.getConfig();
    adaptor.initConfig(config);
    config.overrideKey("filenet.contentEngineUrl", "http://test.example.com");
    config.overrideKey("filenet.username", "test");
    config.overrideKey("filenet.password", "password");
    config.overrideKey("filenet.objectStore", "ObjectStore");
  }

  @Test
  public void testInit() throws Exception {
    adaptor.init(context);
  }

  @Test
  public void testGetConnection() throws Exception {
    // Mock sensitiveValueDecoder uppercases the value.
    Subject subject = new Subject(true, ImmutableSet.<Principal>of(),
        ImmutableSet.of("test"), ImmutableSet.of("PASSWORD"));
    assertFalse(subject.equals(UserContext.get().getSubject()));
    adaptor.init(context);
    try (Connection connection = adaptor.getConnection()) {
      assertEquals("http://test.example.com",
          connection.getConnection().getURI());
      assertTrue(subject.equals(UserContext.get().getSubject()));
    }
    assertFalse(subject.equals(UserContext.get().getSubject()));
  }
}
