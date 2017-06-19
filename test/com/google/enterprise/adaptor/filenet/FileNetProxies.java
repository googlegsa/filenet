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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.IndependentObjectSetMock;
import com.google.enterprise.adaptor.filenet.TraverserFactoryFixture.SearchMock;

import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.collection.PropertyDefinitionList;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.core.Document;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashMap;
import javax.security.auth.Subject;

class FileNetProxies implements ObjectFactory {

  @Override
  public AutoConnection getConnection(String contentEngineUri,
      String username, String password)
      throws EngineRuntimeException {
    return new AutoConnection(
        Proxies.newProxyInstance(com.filenet.api.core.Connection.class,
            new MockConnection(contentEngineUri)),
        new Subject(true, ImmutableSet.<Principal>of(),
            ImmutableSet.of(username), ImmutableSet.of(password)));
  }

  private static class MockConnection {
    private final String contentEngineUri;

    public MockConnection(String contentEngineUri) {
      this.contentEngineUri = contentEngineUri;
    }

    public String getURI() {
      return contentEngineUri;
    }
  }

  private final MockObjectStore objectStore = new MockObjectStore();

  @Override
  public IObjectStore getObjectStore(AutoConnection connection,
      String objectStoreName) throws EngineRuntimeException {
    return objectStore;
  }

  static class MockObjectStore implements IObjectStore {
    private final LinkedHashMap<Id, Document> objects = new LinkedHashMap<>();

    private MockObjectStore() { }

    /**
     * Adds an object to the store.
     */
    public void addObject(Document object) {
      objects.put(object.get_Id(), object);
    }

    /** Verifies that the given object is in the store. */
    public boolean containsObject(String type, Id id) {
      if (ClassNames.DOCUMENT.equals(type)) {
        return objects.containsKey(id);
      } else {
        throw new AssertionError("Unexpected type " + type);
      }
    }

    /** Retrieves all the objects in the store. */
    public Collection<Document> getObjects() {
      return objects.values();
    }

    @Override
    public IBaseObject fetchObject(String type, Id id, PropertyFilter filter) {
      if (ClassNames.DOCUMENT.equals(type)) {
        Document obj = objects.get(id);
        if (obj == null) {
          throw new /*TODO*/ RuntimeException("Unable to fetch document "
              + id);
        } else {
          return new MockDocument(obj);
        }
      } else {
        throw new AssertionError("Unexpected type " + type);
      }
    }
  }

  @Override
  public PropertyDefinitionList getPropertyDefinitions(
      IObjectStore objectStore, Id objectId, PropertyFilter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SearchWrapper getSearch(IObjectStore objectStore) {
    IndependentObjectSet objectSet =
        new IndependentObjectSetMock(
            ((MockObjectStore) objectStore).getObjects());
    return new SearchMock(ImmutableMap.of(ClassNames.DOCUMENT, objectSet));

  }
}
