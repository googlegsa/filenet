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

import static com.google.enterprise.adaptor.IOHelper.copyStream;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.newDocId;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;

import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.DocIdPusher.Record;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.IndependentObjectSetMock;
import com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;

import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.GuidConstants;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.constants.VersionStatus;
import com.filenet.api.core.Document;
import com.filenet.api.core.IndependentObject;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import javax.security.auth.Subject;

class FileNetProxies implements ObjectFactory {
  @Override
  public FileNetAdaptor.Traverser getTraverser(ConfigOptions options) {
    return new MockTraverser(options);
  }

  private static class MockTraverser implements FileNetAdaptor.Traverser {
    private final String idFormat = "{AAAAAAAA-0000-0000-0000-%012d}";
    private final int maxFeedUrls;

    MockTraverser(ConfigOptions configOptions) {
      this.maxFeedUrls = configOptions.getMaxFeedUrls();
    }

    @Override
    public Checkpoint getDocIds(Checkpoint checkpoint, DocIdPusher pusher)
        throws IOException, InterruptedException {
      int counter;
      if (checkpoint.isEmpty()) {
        counter = 0;
      } else {
        String guid = checkpoint.guid;
        counter = Integer.parseInt(
            guid.substring(guid.lastIndexOf('-') + 1, guid.length() - 1));
      }
      boolean crawlImmediately = checkpoint.type.equals("incremental");
      int maxDocIds = maxFeedUrls - 1;
      List<Record> records = new ArrayList<>(maxDocIds);
      for (int i = 0; i < maxDocIds && ++counter < 10000; i++) {
        DocId docid = newDocId(new Id(String.format(idFormat, counter)));
        records.add(new Record.Builder(docid)
            .setCrawlImmediately(crawlImmediately).build());
      }
      if (!records.isEmpty()) {
        Checkpoint newCheckpoint = new Checkpoint(checkpoint.type,
            new Date(), new Id(String.format(idFormat, counter)));
        records.add(new Record.Builder(newDocId(newCheckpoint))
            .setCrawlImmediately(true)
            .build());
        pusher.pushRecords(records);
        return newCheckpoint;
      } else {
        return checkpoint;
      }
    }

    // TODO(bmj): remove me DEBUGGING
    public void getModifiedDocIds(DocIdPusher pusher)
        throws IOException, InterruptedException {
      /*
      String guid = incrementalCheckpoint.guid;
      int counter = Integer.parseInt(
          guid.substring(guid.lastIndexOf('-') + 1, guid.length() - 1));
      int maxDocIds = maxFeedUrls;
      List<Record> records = new ArrayList<>(maxDocIds);
      // Feed two batches per call.
      for (int j = 0; j < 2; j++) {
        for (int i = 0; i < maxDocIds; i++) {
          DocId docid = newDocId(new Id(String.format(idFormat, ++counter)));
          records.add(
              new Record.Builder(docid).setCrawlImmediately(true).build());
        }
        pusher.pushRecords(records);
        records.clear();
      }
      incrementalCheckpoint = new Checkpoint(incrementalCheckpoint.type,
          new Date(), new Id(String.format(idFormat, counter)));
      */
    }

    @Override
    public void getDocContent(Id id, Request request, Response response)
        throws IOException {
      String content = "Hello from document " + id;
      response.setContentType("text/plain");
      copyStream(new ByteArrayInputStream(content.getBytes(UTF_8)),
                 response.getOutputStream());
    }
  }

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

  private final MockObjectStore objectStore = Proxies.newProxyInstance(
      MockObjectStore.class, new MockObjectStoreImpl());

  @Override
  public ObjectStore getObjectStore(AutoConnection connection,
      String objectStoreName) throws EngineRuntimeException {
    return objectStore;
  }

  interface MockObjectStore extends ObjectStore {
    /** Adds an object to the store. */
    void addObject(Document object);
  }

  static class MockObjectStoreImpl {
    private final LinkedHashMap<Id, Document> objects = new LinkedHashMap<>();

    private MockObjectStoreImpl() { }

    /** Adds an object to the store. */
    public void addObject(Document object) {
      objects.put(object.get_Id(), object);

      // Insert the columns used by the various queries into H2.
      String sql = "insert into Document(Id, VersionSeries, DateLastModified, "
          + "VersionStatus, ContentSize) values(?, ?, ?, ?, ?)";
      try (Connection connection = JdbcFixture.getConnection();
          PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, object.get_Id().toString());
        stmt.setString(2, object.get_Id().toString());
        // TODO(jlacey): Hack while we're using the mock objects for inserts.
        try {
          Date modified = object.get_DateLastModified();
          stmt.setTimestamp(3, new Timestamp(modified.getTime()));
          stmt.setInt(4, object.get_VersionStatus().getValue());
          stmt.setObject(5, object.get_ContentSize());
        } catch (AssertionError e) {
          // The mock didn't support one of our called methods; use defaults.
          stmt.setTimestamp(3, new Timestamp(new Date().getTime()));
          stmt.setInt(4, VersionStatus.RELEASED.getValue());
          stmt.setDouble(5, 1000d);
        }
        stmt.executeUpdate();
      } catch (SQLException e) {
        throw new AssertionError(e);
      }
    }

    /* @see ObjectStore#fetchObject */
    public IndependentObject fetchObject(String type, Id id,
        PropertyFilter filter) {
      if (ClassNames.DOCUMENT.equalsIgnoreCase(type)) {
        Document obj = objects.get(id);
        if (obj == null) {
          throw new EngineRuntimeException(ExceptionCode.E_OBJECT_NOT_FOUND);
        } else {
          return obj;
        }
      } else {
        throw new AssertionError("Unexpected type " + type);
      }
    }
  }

  @Override
  public SearchWrapper getSearch(ObjectStore objectStore) {
    return new SearchMock(objectStore);
  }

  // The rest of the tables are in TraverserFactoryFixture.java in v3.
  private static final String CREATE_TABLE_DOCUMENT =
      "create table Document("
      + PropertyNames.ID + " varchar unique, "
      + PropertyNames.DATE_LAST_MODIFIED + " timestamp, "
      + PropertyNames.CONTENT_SIZE + " double, "
      + PropertyNames.NAME + " varchar, "
      + PropertyNames.SECURITY_FOLDER + " varchar, "
      + PropertyNames.SECURITY_POLICY + " varchar, "
      + PropertyNames.VERSION_SERIES + " varchar, "
      + PropertyNames.VERSION_STATUS + " int)";

  static void createTables() throws SQLException {
    JdbcFixture.executeUpdate(CREATE_TABLE_DOCUMENT);
  }

  /**
   * Executes queries against H2 to get the IDs of mock results to return.
   */
  static class SearchMock extends SearchWrapper {
    private final ObjectStore objectStore;

    private SearchMock(ObjectStore objectStore) {
      this.objectStore = objectStore;
    }

    @Override
    public IndependentObjectSet fetchObjects(String query, Integer pageSize,
        PropertyFilter filter, Boolean continuable) {
      // Rewrite queries for H2. Replace GUIDs with table names. Quote
      // timestamps. Rewrite Object(guid) as 'guid'.
      String h2Query = query
          .replace(
              GuidConstants.Class_DeletionEvent.toString(), "DeletionEvent")
          .replace(GuidConstants.Class_Document.toString(), "Document")
          .replace(GuidConstants.Class_Folder.toString(), "Folder")
          .replace(
              GuidConstants.Class_SecurityPolicy.toString(), "SecurityPolicy")
          .replaceAll("([-:0-9]{10}T[-:\\.0-9]{18})", "'$1'")
          .replaceAll("(?i)OBJECT\\((\\{[-0-9A-F]{36}\\})\\)", "'$1'");

      // The page size is ignored for non-continuable queries.
      if (!continuable.booleanValue()) {
        pageSize = Integer.MAX_VALUE;
      } else if (pageSize == null) {
        pageSize = 500; // Mimic ServerCacheCofiguration.QueryPageDefaultSize.
      }

      // Execute the query, and fetch the objects specified by IDs in
      // the result set, limited by the page size.
      try (Statement stmt = JdbcFixture.getConnection().createStatement();
          ResultSet rs = stmt.executeQuery(h2Query)) {
        String tableName = rs.getMetaData().getTableName(1);
        List<IndependentObject> newObjects = new ArrayList<>();
        int count = 0;
        while (rs.next() && count++ < pageSize) {
          newObjects.add(
              objectStore.fetchObject(
                  tableName, new Id(rs.getString(PropertyNames.ID)), filter));
        }
        return new IndependentObjectSetMock(newObjects);
      } catch (SQLException e) {
        throw new EngineRuntimeException(ExceptionCode.DB_ERROR,
            new Object[] { e.getErrorCode(), e.getMessage() });
      }
    }
  }
}
