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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.StartupException;

import com.filenet.api.exception.EngineRuntimeException;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Gets FileNet repository content into a Google Search Appliance. */
public class FileNetAdaptor extends AbstractAdaptor {
  private static final Logger logger =
      Logger.getLogger(FileNetAdaptor.class.getName());

  private final ObjectFactory factory;

  private AdaptorContext context;
  private String contentEngineUrl;
  private String username;
  private String password;
  private String objectStore;

  public static void main(String[] args) {
    AbstractAdaptor.main(new FileNetAdaptor(new FileNetObjectFactory()), args);
  }

  @VisibleForTesting
  FileNetAdaptor(ObjectFactory factory) {
    this.factory = factory;
  }

  @Override
  public void initConfig(Config config) {
    config.addKey("filenet.contentEngineUrl", null);
    config.addKey("filenet.username", null);
    config.addKey("filenet.password", null);
    config.addKey("filenet.objectStore", null);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    this.context = context;
    Config config = context.getConfig();

    contentEngineUrl = config.getValue("filenet.contentEngineUrl");
    logger.log(Level.CONFIG, "filenet.contentEngineUrl: {0}", contentEngineUrl);

    objectStore = config.getValue("filenet.objectStore");
    logger.log(Level.CONFIG, "filenet.objectStore: {0}", objectStore);

    username = config.getValue("filenet.username");
    password = config.getValue("filenet.password");

    // Verify we can connect to the server and access the ObjectStore.
    logger.log(Level.INFO, "Connecting to content engine {0}",
        contentEngineUrl);
    try (Connection connection = getConnection()) {
      logger.log(Level.INFO, "Connecting to object store {0}", objectStore);
      factory.getObjectStore(connection, objectStore);
    } catch (EngineRuntimeException e) {
      throw new StartupException(
          "Failed to access content engine's object store", e);
    }
  }

  @VisibleForTesting
  Connection getConnection() {
    return factory.getConnection(contentEngineUrl, username,
        context.getSensitiveValueDecoder().decodeValue(password));
  }

  @Override
  public void getDocIds(DocIdPusher pusher) {
  }

  @Override
  public void getDocContent(Request req, Response resp) {
  }
}
