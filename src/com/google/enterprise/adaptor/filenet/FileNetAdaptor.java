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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.enterprise.adaptor.DocIdPusher.Record;
import static com.google.enterprise.adaptor.IOHelper.copyStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.US;

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
import com.filenet.api.util.Id;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
  private Traverser documentTraverser;

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

    int maxFeedUrls = Integer.parseInt(config.getValue("feed.maxUrls"));
    documentTraverser = new MockTraverser(context.getDocIdPusher(), maxFeedUrls);
  }

  @VisibleForTesting
  Connection getConnection() {
    return factory.getConnection(contentEngineUrl, username,
        context.getSensitiveValueDecoder().decodeValue(password));
  }

  private static DocId newDocId(Checkpoint checkpoint) {
    return new DocId("pseudo/" + checkpoint);
  }

  private static DocId newDocId(Id id) {
    return new DocId("guid/" + id);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException {
    pusher.pushRecords(Arrays.asList(
        new Record.Builder(newDocId(new Checkpoint("type=document")))
            .setCrawlImmediately(true).build()));
  }

  @Override
  public void getDocContent(Request req, Response resp) throws IOException,
      InterruptedException {
    DocId id = req.getDocId();
    String[] idParts = id.getUniqueId().split("/", 2);
    if (idParts.length != 2) {
      logger.log(Level.FINE, "Invalid DocId: {0}", id);
      resp.respondNotFound();
      return;
    }
    switch (idParts[0]) {
      case "pseudo":
        Checkpoint checkpoint = new Checkpoint(idParts[1]);
        switch (checkpoint.traverser) {
          case "document":
            documentTraverser.getDocIds(checkpoint);
            break;
          default:
            logger.log(Level.WARNING, "Unsupported traverser: " + checkpoint);
            break;
        }
        break;
      case "guid":
        documentTraverser.getDocContent(new Id(idParts[1]), resp);
        break;
      default:
        logger.log(Level.FINE, "Invalid DocId: {0}", id);
        resp.respondNotFound();
        return;
    }
  }

  @VisibleForTesting
  static interface Traverser {
    void getDocIds(Checkpoint checkpoint)
        throws IOException, InterruptedException;

    void getDocContent(Id id, Response response)
        throws IOException, InterruptedException;
  }

  @VisibleForTesting
  static class MockTraverser implements Traverser {
    private final String idFormat = "{AAAAAAAA-0000-0000-0000-%012d}";
    private final DocIdPusher pusher;
    private final int maxFeedUrls;

    MockTraverser(DocIdPusher pusher, int maxFeedUrls) {
      this.pusher = checkNotNull(pusher, "pusher must not be null");
      checkArgument(maxFeedUrls > 2, "feed.maxUrls must be greater than 2");
      this.maxFeedUrls = maxFeedUrls;
    }

    @Override
    public void getDocIds(Checkpoint checkpoint)
        throws IOException, InterruptedException {
      Date timestamp;
      int id;
      if (checkpoint.uuid == null) {
        timestamp = new Date();
        id = 0;
      } else {
        timestamp = checkpoint.timestamp;
        String idStr = checkpoint.uuid.toString();
        id = Integer.parseInt(
            idStr.substring(idStr.lastIndexOf('-') + 1, idStr.length() - 1));
      }
      int maxDocIds = maxFeedUrls - 1;
      List<Record> records = new ArrayList<>(maxDocIds);
      for (int i = 0; i < maxDocIds && id < 10000; i++, id++) {
        DocId docid = newDocId(new Id(String.format(idFormat, id)));
        records.add(new Record.Builder(docid).build());
      }
      Checkpoint newCheckpoint;
      if (records.isEmpty()) {
        // No new data, push old checkpoint;
        newCheckpoint = checkpoint;
      } else {
        newCheckpoint = new Checkpoint(checkpoint.traverser, timestamp,
            new Id(String.format(idFormat, id)));
      }
      records.add(new Record.Builder(newDocId(newCheckpoint))
          .setCrawlImmediately(true)
          //.setLastModified(new Date()) TODO(bmj)
          .build());
      pusher.pushRecords(records);
    }

    @Override
    public void getDocContent(Id id, Response response) throws IOException {
      checkNotNull(id, "id must not be null");
      checkNotNull(response, "response must not be null");
      String content = "Hello from document " + id;
      response.setContentType("text/plain");
      copyStream(new ByteArrayInputStream(content.getBytes(UTF_8)),
                 response.getOutputStream());
    }
  }

  @VisibleForTesting
  static class Checkpoint {
    private static final MessageFormat SHORT_FORMAT = new MessageFormat(
        "type={0}", US);
    private static final MessageFormat FULL_FORMAT = new MessageFormat(
        "type={0};timestamp={1,date,yyyy-MM-dd'T'HH:mm:ss.SSSZ};guid={2}", US);

    public String traverser;
    public Date timestamp;
    public Id uuid;

    @SuppressWarnings("fallthrough")
    public Checkpoint(String checkpoint) {
      checkNotNull(checkpoint, "checkpoint may not be null");
      logger.info("Checkpoint: '" + checkpoint + "'");
      Object[] objs;
      try {
        if (checkpoint.indexOf(';') < 0) {
          objs = SHORT_FORMAT.parse(checkpoint);
        } else {
          objs = FULL_FORMAT.parse(checkpoint);
        }
      } catch (ParseException e) {
        throw new IllegalArgumentException(
            "Invalid Checkpoint: " + checkpoint, e);
      }
      switch (objs.length) {
        case 3:
          timestamp = (Date) objs[1];
          logger.info("timestamp: " + timestamp.toString());
          uuid = new Id((String) objs[2]);
          logger.info("guid: " + timestamp.toString());
          // fall through
        case 1:
          traverser = (String) objs[0];
          logger.info("traverser: " + traverser);
          break;
        default:
          throw new IllegalArgumentException(
              "Invalid Checkpoint: " + checkpoint);
      }
    }

    public Checkpoint(String traverser, Date timestamp, Id uuid) {
      this.traverser = checkNotNull(traverser, "traverser may not be null");
      this.timestamp = timestamp;
      this.uuid = uuid;
    }

    @Override
    public String toString() {
      if (timestamp == null && uuid == null) {
        return SHORT_FORMAT.format(new Object[] {traverser});
      } else {
        return FULL_FORMAT.format(
            new Object[] {traverser, timestamp, uuid.toString()});
      }
    }
  }
}
