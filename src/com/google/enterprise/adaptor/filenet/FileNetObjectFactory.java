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

import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.util.UserContext;

import javax.security.auth.Subject;

/**
 * Factory for producing instances various FileNet Objects.
 */
class FileNetObjectFactory implements ObjectFactory {

  @Override
  public Connection getConnection(String contentEngineUri,
      String username, String password)
      throws EngineRuntimeException {
    com.filenet.api.core.Connection connection =
        Factory.Connection.getConnection(contentEngineUri);
    Subject subject =
        UserContext.createSubject(connection, username, password, "FileNetP8");
    return new Connection(connection, subject);
  }

  @Override
  public ObjectStore getObjectStore(Connection connection,
      String objectStoreName) throws EngineRuntimeException {
    Domain domain = Factory.Domain.fetchInstance(
        connection.getConnection(), null, null);
    return Factory.ObjectStore.fetchInstance(domain, objectStoreName, null);
  }
}
