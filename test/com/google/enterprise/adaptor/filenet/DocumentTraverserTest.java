// Copyright 2007-2008 Google Inc. All Rights Reserved.
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

import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint.getQueryTimeString;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.newDocId;
import static com.google.enterprise.adaptor.filenet.ObjectMocks.mockDocument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher.Record;
import com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;
import com.google.enterprise.adaptor.filenet.FileNetProxies.MockObjectStore;
import com.google.enterprise.adaptor.testing.RecordingDocIdPusher;
import com.google.enterprise.adaptor.testing.RecordingResponse;

import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.util.Id;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Tests for DocumentTraverser. */
public class DocumentTraverserTest {
  private static final SimpleDateFormat dateFormatter =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

  private static final Checkpoint EMPTY_CHECKPOINT =
      new Checkpoint("document", null, null);

  private static final Checkpoint CHECKPOINT =
      new Checkpoint("type=document;timestamp=1990-01-01T00:00:00.000-02:00;"
          + "guid={AAAAAAAA-0000-0000-0000-000000000000}");

  private static final String DOCUMENT_TIMESTAMP =
      "2014-01-01T20:00:00.000";

  private static final String CHECKPOINT_TIMESTAMP;

  static {
    try {
      CHECKPOINT_TIMESTAMP =
          getQueryTimeString(dateFormatter.parse(DOCUMENT_TIMESTAMP));
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  private ConfigOptions options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws SQLException {
    options = TestObjectFactory.newConfigOptions();
    FileNetProxies.createTables();
  }

  @After
  public void tearDown() throws SQLException {
    JdbcFixture.dropAllObjects();
  }

  private MockObjectStore getObjectStore() {
    return (MockObjectStore) options.getObjectStore(null);
  }

  @Test
  public void testGetDocIds_emptyNoResults() throws Exception {
    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);
    assertEquals(ImmutableList.of(), pusher.getRecords());
  }

  @Test
  public void testGetDocIds_emptyWithResults() throws Exception {
    MockObjectStore objectStore = getObjectStore();
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    Date now = new Date();
    String lastModified = dateFormatter.format(now);
    mockDocument(objectStore, id, lastModified, true,
        TestObjectFactory.getPermissions(PermissionSource.SOURCE_DIRECT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);

    assertEquals(ImmutableList.of(new Id(id)), getIds(pusher.getRecords()));
    String checkpoint = getCheckpoint(pusher.getRecords());
    assertTrue(checkpoint, checkpoint.contains(id));
    assertTrue(checkpoint,
        checkpoint.contains(Checkpoint.getQueryTimeString(now)));
  }

  private ImmutableList<Id> getIds(List<Record> docList) {
    ImmutableList.Builder<Id> builder = ImmutableList.builder();
    for (Record record : docList.subList(0, docList.size() - 1)) {
      String s = record.getDocId().getUniqueId();
      assertThat(s, startsWith("guid/"));
      assertFalse("Record is crawl-immediately",
          record.isToBeCrawledImmediately());
      builder.add(new Id(s.substring(5)));
    }
    return builder.build();
  }

  private String getCheckpoint(List<Record> docList) {
    assertTrue(docList.toString(), docList.size() > 1);
    Record record = docList.get(docList.size() - 1);
    String s = record.getDocId().getUniqueId();
    assertThat(s, startsWith("pseudo/"));
    assertTrue("Record is not crawl-immediately",
        record.isToBeCrawledImmediately());
    return s.substring(7);
  }

  @Test
  public void testGetCheckpointClause() throws Exception {
    String expectedId = "{AAAAAAAA-0000-0000-0000-000000000000}";
    Date expectedDate = new Date();
    String expectedDateString = Checkpoint.getQueryTimeString(expectedDate);

    DocumentTraverser traverser = new DocumentTraverser(options);
    String whereClause = traverser.getCheckpointClause(
        new Checkpoint("document", expectedDate, new Id(expectedId)));

    assertEquals(
        " AND ((DateLastModified=" + expectedDateString
            + " AND ('" + expectedId + "'<Id)) OR (DateLastModified>"
            + expectedDateString + "))",
        whereClause);
  }

  private String[][] docEntries = {
    { "AAAAAAAA-1000-0000-0000-000000000000", DOCUMENT_TIMESTAMP },
    { "AAAAAAAA-2000-0000-0000-000000000000", DOCUMENT_TIMESTAMP },
    { "AAAAAAAA-3000-0000-0000-000000000000", DOCUMENT_TIMESTAMP },
    { "AAAAAAAA-4000-0000-0000-000000000000", DOCUMENT_TIMESTAMP },
  };

  @Test
  public void testGetDocIds_noResults() throws Exception {
    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    assertEquals(ImmutableList.of(), pusher.getRecords());
  }

  @Test
  public void testGetDocIds_emptyCheckpoint() throws Exception {
    addDocuments(docEntries);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);
    List<Record> docList = pusher.getRecords();
    for (Id id : getIds(docList)) {
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-4000-0000-0000-000000000000}");
  }

  // TODO(jlacey): Once SearchMock uses H2 to get the objects, add a
  // test that with a checkpoint that skips some data.
  @Test
  public void testGetDocIds_checkpoint() throws Exception {
    addDocuments(docEntries);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Record> docList = pusher.getRecords();
    for (Id id : getIds(docList)) {
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-4000-0000-0000-000000000000}");
  }

  // TODO(jlacey): This is not interesting against the current mocks,
  // but it could become so with H2-backed proxies, or even made so
  // with better mocks.
  @Test
  public void testGetDocIds_monotonicDates() throws Exception {
    addDocuments(docEntries);

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Id> docList = getIds(pusher.getRecords());
    assertFalse(docList.isEmpty());

    int counter = 0;
    Date prevDate = new Date(0L);
    for (Id id : docList) {
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      Date thisDate = response.getLastModified();
      assertTrue("Previous date " + prevDate + " is after " + thisDate,
          prevDate.compareTo(thisDate) <= 0);
      prevDate = thisDate;
      counter++;
    }
    assertEquals(docEntries.length, counter);
  }

  @Test
  public void testGetDocIds_batchHint() throws Exception {
    options = TestObjectFactory.newConfigOptions(
        ImmutableMap.<String, String>of("feed.maxUrls", "2"));
    addDocuments(docEntries);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = new DocumentTraverser(options);
    assertTrue(String.valueOf(docEntries.length), docEntries.length > 1);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Record> docList = pusher.getRecords();
    for (Id id : getIds(docList)) {
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(1, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-1000-0000-0000-000000000000}");
  }

  @Test
  public void testMimeTypesAndSizes() throws Exception {
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 32, true);
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 1024 * 3L, false);
    testMimeTypeAndContentSize("video/3gpp", 1024 * 1024 * 100, true);
  }

  private void testMimeTypeAndContentSize(String mimeType, double size,
      boolean expectNotNull) throws Exception {
    MockObjectStore os = getObjectStore();
    mockDocument(os, "AAAAAAA1", DOCUMENT_TIMESTAMP, false, size, mimeType);

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Id> docList = getIds(pusher.getRecords());

    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(docList.get(0), null, response);

    String expectedContent = (expectNotNull) ? "sample content" : "";
    byte[] actualContent =
        ((ByteArrayOutputStream) response.getOutputStream()).toByteArray();
    assertEquals(expectedContent, new String(actualContent, UTF_8));
  }

  @Test
  public void testGetDocContent() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();
    mockDocument(os, id, DOCUMENT_TIMESTAMP, true,
        TestObjectFactory.getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(new Id(id), null, response);

    assertEquals(
        ImmutableSet.of(PropertyNames.ID, PropertyNames.DATE_LAST_MODIFIED),
        response.getMetadata().getKeys());

    Acl acl = response.getAcl();
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals("TMPL", acl.getInheritFromFragment());

    acl = response.getNamedResources().get("TMPL");
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals("FLDR", acl.getInheritFromFragment());

    acl = response.getNamedResources().get("FLDR");
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(null, acl.getInheritFrom());
    assertEquals(null, acl.getInheritFromFragment());
  }

  /**
   * Creates an object set of documents.
   *
   * @param entries an array of arrays of IDs and timestamps
   */
  private void addDocuments(String[][] entries) {
    MockObjectStore os = getObjectStore();
    for (String[] entry : entries) {
      mockDocument(os, entry[0], entry[1], /* releasedVersion */ true);
    }
  }

  private void assertCheckpointEquals(String actualCheckpoint,
      String expectedDate, String expectedId) {
    assertFalse("Missing checkpoint: " + actualCheckpoint,
        Strings.isNullOrEmpty(actualCheckpoint));
    assertNotNull("Null expected date", expectedDate);
    assertNotNull("Null expected guid", expectedId);

    Checkpoint checkpoint = new Checkpoint(actualCheckpoint);
    assertEquals(expectedDate, checkpoint.timestamp);
    assertEquals(expectedId, checkpoint.guid);
  }
}
