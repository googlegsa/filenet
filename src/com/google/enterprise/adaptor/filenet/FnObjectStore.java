// Copyright 2008 Google Inc. All Rights Reserved.
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
import com.filenet.api.core.IndependentObject;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

import java.util.logging.Level;
import java.util.logging.Logger;

class FnObjectStore implements IObjectStore {
  private static final Logger logger =
      Logger.getLogger(FnObjectStore.class.getName());

  private final ObjectStore objectStore;

  public FnObjectStore(ObjectStore objectStore) {
    this.objectStore = objectStore;
  }

  @Override
  public IBaseObject fetchObject(String type, Id id, PropertyFilter filter) {
    IndependentObject obj = null;
    try {
      obj = objectStore.fetchObject(type, id, filter);
      // TODO(jlacey): Handling DeletionEvents fetches VersionSeries
      // objects, so we may need this later, but it's blocking the
      // removal of IVersionSeries. The planned for removal of
      // IDocument would allow us to put this code back (and return
      // IndependentObject).
      //
      // if (type.equals(ClassNames.VERSION_SERIES)) {
      //   return new FnVersionSeries((VersionSeries) obj);
      // }
      if (type.equals(ClassNames.DOCUMENT)) {
        return new FnDocument((Document) obj);
      } else {
        // TODO(jlacey): This exception may not be caught if we
        // refactor this to throw EngineRuntimeException, but that
        // doesn't have a String constructor.
        throw new IllegalArgumentException("Unexpected object type: " + type);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,
          "Unable to fetch VersionSeries or Document object", e);
      throw new /*TODO*/ RuntimeException(e);
    }
  }

  ObjectStore getObjectStore() {
    return objectStore;
  }
}
