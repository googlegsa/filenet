// Copyright 2017 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.filenet;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;

import com.filenet.api.collection.PropertyDefinitionList;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

/**
 * Returns null or empty nice mocks. This class itself must be
 * public and instantiable from a class name by FileSession.
 */
public class MockObjectFactory implements ObjectFactory {
  @Override
  public Connection getConnection(String contentEngineUri, String userName,
      String userPassword) {
    Connection connection = createNiceMock(Connection.class);
    replay(connection);
    return connection;
  }

  @Override
  public IObjectStore getObjectStore(Connection connection,
      String objectStoreName) {
    return null;
  }

  @Override
  public PropertyDefinitionList getPropertyDefinitions(
      IObjectStore objectStore, Id objectId, PropertyFilter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SearchWrapper getSearch(IObjectStore objectStore) {
    SearchWrapper search = createNiceMock(SearchWrapper.class);
    replay(search);
    return search;
  }
}

