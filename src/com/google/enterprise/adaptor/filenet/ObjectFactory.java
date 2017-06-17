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

import com.filenet.api.collection.PropertyDefinitionList;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

/**
 * Factory for producing instances various FileNet Objects.
 */
interface ObjectFactory {
  Connection getConnection(String contentEngineUri,
      String username, String password)
      throws EngineRuntimeException;

  IObjectStore getObjectStore(Connection connection,
      String objectStoreName) throws EngineRuntimeException;

  PropertyDefinitionList getPropertyDefinitions(
      IObjectStore objectStore, Id objectId, PropertyFilter filter)
      throws EngineRuntimeException;

  SearchWrapper getSearch(IObjectStore objectStore);
}
