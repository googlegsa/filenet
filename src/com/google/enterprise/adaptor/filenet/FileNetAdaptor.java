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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.enterprise.adaptor.DocIdPusher.Record;
import static com.google.enterprise.adaptor.Principal.DEFAULT_NAMESPACE;
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

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gets FileNet repository content into a Google Search Appliance. */
public class FileNetAdaptor extends AbstractAdaptor {
  private static final Logger logger =
      Logger.getLogger(FileNetAdaptor.class.getName());

  private ObjectFactory factory;
  private AdaptorContext context;
  private ConfigOptions configOptions;

  private Traverser documentTraverser;

  public static void main(String[] args) {
    AbstractAdaptor.main(new FileNetAdaptor(), args);
  }

  @Override
  public void initConfig(Config config) {
    config.addKey("filenet.contentEngineUrl", null);
    config.addKey("filenet.username", null);
    config.addKey("filenet.password", null);
    config.addKey("filenet.objectStore", null);
    config.addKey("filenet.objectFactory",
        FileNetObjectFactory.class.getName());
    config.addKey("filenet.displayUrl", null);
    config.addKey("filenet.additionalWhereClause", "");
    config.addKey("filenet.deleteAdditionalWhereClause", "");
    config.addKey("filenet.excludedMetadata", "");
    config.addKey("filenet.includedMetadata", "");
    config.addKey("adaptor.namespace", DEFAULT_NAMESPACE);
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
    this.context = context;
    this.configOptions = new ConfigOptions(context);

    try (AutoConnection connection = configOptions.getConnection()) {
      configOptions.getObjectStore(connection);
    } catch (EngineRuntimeException e) {
      throw new StartupException(
          "Failed to access content engine's object store", e);
    }

    documentTraverser =
        configOptions.getObjectFactory().getTraverser(configOptions);
  }

  @VisibleForTesting
  ConfigOptions getConfigOptions() {
    return configOptions;
  }

  static DocId newDocId(Checkpoint checkpoint) {
    return new DocId("pseudo/" + checkpoint);
  }

  static DocId newDocId(Id id) {
    return new DocId("guid/" + id);
  }

  @Override
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException {
    pusher.pushRecords(Arrays.asList(
        new Record.Builder(newDocId(new Checkpoint("document", null, null)))
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
        switch (checkpoint.type) {
          case "document":
            documentTraverser.getDocIds(checkpoint, context.getDocIdPusher());
            resp.setNoIndex(true);
            resp.setContentType("text/plain");
            resp.getOutputStream().write(" ".getBytes(UTF_8));
            break;
          default:
            logger.log(Level.WARNING, "Unsupported type: " + checkpoint);
            resp.respondNotFound();
            break;
        }
        break;
      case "guid":
        Id guid;
        try {
          guid = new Id(idParts[1]);
        } catch (EngineRuntimeException e) {
          logger.log(Level.FINE, "Invalid DocId: " + id, e);
          resp.respondNotFound();
          return;
        }
        documentTraverser.getDocContent(guid, req, resp);
        break;
      default:
        logger.log(Level.FINE, "Invalid DocId: {0}", id);
        resp.respondNotFound();
        return;
    }
  }

  static interface Traverser {
    void getDocIds(Checkpoint checkpoint, DocIdPusher pusher)
        throws IOException, InterruptedException;

    void getDocContent(Id id, Request request, Response response)
        throws IOException, InterruptedException;
  }

  @VisibleForTesting
  static class Checkpoint {
    private static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final Pattern ZONE_PATTERN =
        Pattern.compile("(?:(Z)|([+-][0-9]{2})(:)?([0-9]{2})?)$");
    private static final String ZULU_WITH_COLON = "+00:00";
    private static final MessageFormat SHORT_FORMAT = new MessageFormat(
        "type={0}", US);
    private static final MessageFormat FULL_FORMAT = new MessageFormat(
        "type={0};timestamp={1};guid={2}", US);

    public final String type;
    public final String timestamp;
    public final String guid;

    public Checkpoint(String checkpoint) {
      checkNotNull(checkpoint, "checkpoint may not be null");
      logger.info("Checkpoint: '" + checkpoint + "'");
      try {
        if (checkpoint.indexOf(';') < 0) {
          Object[] objs = SHORT_FORMAT.parse(checkpoint);
          type = (String) objs[0];
          timestamp = null;
          guid = null;
        } else {
          Object[] objs = FULL_FORMAT.parse(checkpoint);
          type = (String) objs[0];
          timestamp = (String) objs[1];
          guid = (String) objs[2];
        }
      } catch (ParseException e) {
        throw new IllegalArgumentException(
            "Invalid Checkpoint: " + checkpoint, e);
      }
    }

    public Checkpoint(String type, Date timestamp, Id guid) {
      this.type = checkNotNull(type, "type may not be null");
      this.timestamp =
          (timestamp == null) ? null : getQueryTimeString(timestamp);
      this.guid = (guid == null) ? null : guid.toString();
    }

    /**
     * Validate the time string by:
     * (a) appending zone portion (+/-hh:mm) or
     * (b) inserting the colon into zone portion
     * if it does not already have zone or colon.
     *
     * Adapted from the v3 connector's FileUtil.java
     *
     * @param timestamp a Date
     * @return String - date time in ISO8601 format including zone
     */
    @VisibleForTesting
    static String getQueryTimeString(Date timestamp) {
      String checkpoint = new SimpleDateFormat(ISO_8601).format(timestamp);
      Matcher matcher = ZONE_PATTERN.matcher(checkpoint);
      if (matcher.find()) {
        String timeZone = matcher.group();
        if (timeZone.length() == 5) {
          return checkpoint.substring(0, matcher.start())
              + timeZone.substring(0, 3) + ":" + timeZone.substring(3);
        } else if (timeZone.length() == 3) {
          return checkpoint + ":00";
        } else {
          return checkpoint.replaceFirst("Z$", ZULU_WITH_COLON);
        }
      } else {
        return checkpoint + ZULU_WITH_COLON;
      }
    }

    public boolean isEmpty() {
      return (timestamp == null && guid == null);
    }

    @Override
    public String toString() {
      if (isEmpty()) {
        return SHORT_FORMAT.format(new Object[] {type});
      } else {
        return FULL_FORMAT.format(new Object[] {type, timestamp, guid});
      }
    }
  }
}
