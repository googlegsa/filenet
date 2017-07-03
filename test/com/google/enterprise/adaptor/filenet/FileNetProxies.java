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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
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
    public void getDocIds(Checkpoint checkpoint, DocIdPusher pusher)
        throws IOException, InterruptedException {
      int counter;
      if (checkpoint.isEmpty()) {
        counter = 0;
      } else {
        String guid = checkpoint.guid;
        counter = Integer.parseInt(
            guid.substring(guid.lastIndexOf('-') + 1, guid.length() - 1));
      }
      int maxDocIds = maxFeedUrls - 1;
      List<Record> records = new ArrayList<>(maxDocIds);
      for (int i = 0; i < maxDocIds && ++counter < 10000; i++) {
        DocId docid = newDocId(new Id(String.format(idFormat, counter)));
        records.add(new Record.Builder(docid).build());
      }
      if (!records.isEmpty()) {
        Checkpoint newCheckpoint = new Checkpoint(checkpoint.type,
            new Date(), new Id(String.format(idFormat, counter)));
        records.add(new Record.Builder(newDocId(newCheckpoint))
            .setCrawlImmediately(true)
            .build());
        pusher.pushRecords(records);
      }
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

    /** Retrieves all the objects in the store. */
    Collection<Document> getObjects();
  }

  static class MockObjectStoreImpl {
    private final LinkedHashMap<Id, Document> objects = new LinkedHashMap<>();

    private MockObjectStoreImpl() { }

    /** Adds an object to the store. */
    public void addObject(Document object) {
      objects.put(object.get_Id(), object);
    }

    /** Retrieves all the objects in the store. */
    public Collection<Document> getObjects() {
      return objects.values();
    }

    /* @see ObjectStore#fetchObject */
    public IndependentObject fetchObject(String type, Id id,
        PropertyFilter filter) {
      if (ClassNames.DOCUMENT.equals(type)) {
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
    IndependentObjectSet objectSet =
        new IndependentObjectSetMock(
            ((MockObjectStore) objectStore).getObjects());
    return new SearchMock(ImmutableMap.of(ClassNames.DOCUMENT, objectSet));
  }

  // The rest of the tables are in TraverserFactoryFixture.java in v3.
  private static final String CREATE_TABLE_DOCUMENT =
      "create table Document("
      + PropertyNames.ID + " varchar, "
      + PropertyNames.DATE_LAST_MODIFIED + " timestamp, "
      + PropertyNames.CONTENT_SIZE + " int, "
      + PropertyNames.NAME + " varchar, "
      + PropertyNames.SECURITY_FOLDER + " varchar, "
      + PropertyNames.SECURITY_POLICY + " varchar, "
      + PropertyNames.VERSION_SERIES + " varchar, "
      + PropertyNames.VERSION_STATUS + " int)";

  static void createTables() throws SQLException {
    JdbcFixture.executeUpdate(CREATE_TABLE_DOCUMENT);
  }

  /**
   * Smoke tests the queries against H2 but returns mock results.
   */
  static class SearchMock extends SearchWrapper {
    /** A map with case-insensitive keys for natural table name matching. */
    private final ImmutableSortedMap<String, IndependentObjectSet> results;

    /**
     * Constructs a mock to return the given results for each table.
     *
     * @param results a map from table names to the object sets to
     *     return as results for queries against those tables
     */
    protected SearchMock(
        ImmutableMap<String, ? extends IndependentObjectSet> results) {
      this.results = ImmutableSortedMap.<String, IndependentObjectSet>orderedBy(
          String.CASE_INSENSITIVE_ORDER).putAll(results).build();
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

      // Execute the queries.
      try (Statement stmt = JdbcFixture.getConnection().createStatement();
          ResultSet rs = stmt.executeQuery(h2Query)) {
        // Look up the results to return by table name.
        String tableName = rs.getMetaData().getTableName(1);
        IndependentObjectSet set = results.get(tableName);

        if (set == null) {
          new IndependentObjectSetMock(ImmutableSet.<IndependentObject>of());
        }

        // We can't get the size of objectSet easily, so we always
        // copy the objects, limited by the page size.
        Iterator<?> oldObjects = set.iterator();
        List<IndependentObject> newObjects = new ArrayList<>();
        int count = 0;
        while (oldObjects.hasNext() && count++ < pageSize) {
          IndependentObject object = (IndependentObject) oldObjects.next();
          // Check to see if the results should be selective, and
          // return the single document asked for in the query.
          // TODO(jlacey): Checking for "SELECT Id FROM" is a hack. Use H2.
          if (!query.startsWith("SELECT Id FROM")
              || (object instanceof Document
                  && query.contains(((Document) object).get_Id().toString()))) {
            newObjects.add(object);
          }
        }
        return new IndependentObjectSetMock(newObjects);
      } catch (SQLException e) {
        throw new EngineRuntimeException(ExceptionCode.DB_ERROR,
            new Object[] { e.getErrorCode(), e.getMessage() });
      }
    }
  }
}
