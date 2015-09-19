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

import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.util.UserContext;

import javax.security.auth.Subject;

/**
 * Wraps a FileNet Connection while maintaining the FileNet UserContext.
 * Note that pushSubject() and popSubject() must be balanced within a thread.
 */
class Connection implements AutoCloseable {

  private final com.filenet.api.core.Connection connection;

  Connection(com.filenet.api.core.Connection connection, Subject subject)
      throws EngineRuntimeException {
    this.connection = connection;
    UserContext.get().pushSubject(subject);
  }

  @Override
  public void close() {
    UserContext.get().popSubject();
  }

  /** Returns the underlying FileNet Connection. */
  com.filenet.api.core.Connection getConnection() {
    return connection;
  }
}