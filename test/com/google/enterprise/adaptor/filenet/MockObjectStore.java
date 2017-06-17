// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.filenet.api.constants.ClassNames;
import com.filenet.api.core.Document;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

import java.util.HashMap;

class MockObjectStore implements IObjectStore {
  private final HashMap<Id, Document> objects = new HashMap<>();

  public MockObjectStore() {
  }

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
