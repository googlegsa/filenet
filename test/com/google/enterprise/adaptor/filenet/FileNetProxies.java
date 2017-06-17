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

import com.google.common.collect.ImmutableSet;

import com.filenet.api.collection.PropertyDefinitionList;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.util.Id;

import java.security.Principal;
import javax.security.auth.Subject;

class FileNetProxies implements ObjectFactory {

  @Override
  public Connection getConnection(String contentEngineUri,
      String username, String password)
      throws EngineRuntimeException {
    return new Connection(
        Proxies.newProxyInstance(com.filenet.api.core.Connection.class,
            new FileNetConnectionMock(contentEngineUri)),
        new Subject(true, ImmutableSet.<Principal>of(),
            ImmutableSet.of(username), ImmutableSet.of(password)));
  }

  @Override
  public IObjectStore getObjectStore(Connection connection,
      String objectStoreName) throws EngineRuntimeException {
    return Proxies.newProxyInstance(IObjectStore.class,
        new ObjectStoreMock(objectStoreName));
  }

  private class FileNetConnectionMock {
    private final String contentEngineUri;

    public FileNetConnectionMock(String contentEngineUri) {
      this.contentEngineUri = contentEngineUri;
    }

    public String getURI() {
      return contentEngineUri;
    }
  }

  private class ObjectStoreMock {
    private final String objectStoreName;

    public ObjectStoreMock(String objectStoreName) {
      this.objectStoreName = objectStoreName;
    }

    public String get_Name() {
      return objectStoreName;
    }
  }

  @Override
  public PropertyDefinitionList getPropertyDefinitions(
      IObjectStore objectStore, Id objectId, PropertyFilter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SearchWrapper getSearch(IObjectStore objectStore) {
    throw new UnsupportedOperationException();
  }
}
