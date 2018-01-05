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

import static com.filenet.api.constants.VersionStatus.RELEASED;
import static com.google.enterprise.adaptor.Acl.InheritanceType;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint.getQueryTimeString;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.newDocId;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.percentEscape;
import static com.google.enterprise.adaptor.filenet.ObjectMocks.mockActiveMarking;
import static com.google.enterprise.adaptor.filenet.ObjectMocks.mockDocument;
import static com.google.enterprise.adaptor.filenet.ObjectMocks.mockDocumentNotFound;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher.Record;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.ActiveMarkingListMock;
import com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;
import com.google.enterprise.adaptor.filenet.FileNetProxies.MockObjectStore;
import com.google.enterprise.adaptor.testing.RecordingDocIdPusher;
import com.google.enterprise.adaptor.testing.RecordingResponse;

import com.filenet.api.constants.AccessLevel;
import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.property.FilterElement;
import com.filenet.api.property.PropertyFilter;
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
  private static final String DOCUMENT_DATE =
      DOCUMENT_TIMESTAMP.substring(0, DOCUMENT_TIMESTAMP.indexOf('T'));

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
    List<Record> docList = pusher.getRecords();
    assertEquals(docList.toString(), 1, docList.size());
    assertCheckpointEquals(EMPTY_CHECKPOINT, getDeleteCheckpoint(docList));
  }

  @Test
  public void testGetDocIds_emptyWithResults() throws Exception {
    MockObjectStore objectStore = getObjectStore();
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    Date now = new Date();
    String lastModified = dateFormatter.format(now);
    mockDocument(objectStore, id, lastModified, RELEASED, 42d, "text/plain",
        TestObjectFactory.getPermissions(PermissionSource.SOURCE_DIRECT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);

    List<Record> docList = pusher.getRecords();
    assertEquals(ImmutableList.of(new Id(id)), getIds(docList));
    Checkpoint expected = new Checkpoint(CHECKPOINT.type, now, new Id(id));
    assertCheckpointEquals(expected, getCheckpoint(docList));
    assertCheckpointEquals(EMPTY_CHECKPOINT, getDeleteCheckpoint(docList));
  }

  private ImmutableList<Id> getIds(List<Record> docList) {
    assertTrue(docList.toString(), docList.size() > 2);
    ImmutableList.Builder<Id> builder = ImmutableList.builder();
    for (Record record : docList.subList(0, docList.size() - 2)) {
      String s = record.getDocId().getUniqueId();
      assertThat(s, startsWith("guid/"));
      assertFalse("Record is crawl-immediately: " + record,
          record.isToBeCrawledImmediately());
      builder.add(new Id(s.substring(5)));
    }
    return builder.build();
  }

  private Checkpoint newCheckpoint(String type, String timestamp, String guid) {
    return new Checkpoint(Checkpoint.FULL_FORMAT.format(
        new Object[] {type, timestamp, guid}));
  }

  private Checkpoint getCheckpoint(List<Record> docList) {
    assertTrue(docList.toString(), docList.size() > 2);
    Record record = docList.get(docList.size() - 2);
    String s = record.getDocId().getUniqueId();
    assertThat(s, startsWith("pseudo/"));
    assertTrue("Record is not crawl-immediately: " + record,
        record.isToBeCrawledImmediately());
    return new Checkpoint(s.substring(7));
  }

  private Checkpoint getDeleteCheckpoint(List<Record> docList) {
    assertTrue(docList.toString(), docList.size() > 0);
    Record record = docList.get(docList.size() - 1);
    String s = record.getDocId().getUniqueId();
    assertThat(s, startsWith("pseudo/"));
    assertTrue("Record is not a delete: " + record, record.isToBeDeleted());
    return new Checkpoint(s.substring(7));
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
    List<Record> docList = pusher.getRecords();
    assertEquals(docList.toString(), 1, docList.size());
    assertCheckpointEquals(CHECKPOINT, getDeleteCheckpoint(docList));
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
      Request request = new MockRequest(newDocId(id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, request, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(newCheckpoint("document", CHECKPOINT_TIMESTAMP,
        "{AAAAAAAA-4000-0000-0000-000000000000}"), getCheckpoint(docList));
    assertCheckpointEquals(EMPTY_CHECKPOINT, getDeleteCheckpoint(docList));
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
      Request request = new MockRequest(newDocId(id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, request, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(newCheckpoint("document", CHECKPOINT_TIMESTAMP,
        "{AAAAAAAA-4000-0000-0000-000000000000}"), getCheckpoint(docList));
    assertCheckpointEquals(CHECKPOINT, getDeleteCheckpoint(docList));
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
      Request request = new MockRequest(newDocId(id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, request, response);
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
        ImmutableMap.<String, String>of("feed.maxUrls", "3"));
    addDocuments(docEntries);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = new DocumentTraverser(options);
    assertTrue(String.valueOf(docEntries.length), docEntries.length > 1);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Record> docList = pusher.getRecords();
    for (Id id : getIds(docList)) {
      Request request = new MockRequest(newDocId(id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, request, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(1, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(newCheckpoint("document", CHECKPOINT_TIMESTAMP,
        "{AAAAAAAA-1000-0000-0000-000000000000}"), getCheckpoint(docList));
    assertCheckpointEquals(CHECKPOINT, getDeleteCheckpoint(docList));
  }

  @Test
  public void testGetModifiedDocIds_noneNew() throws Exception {
    options = TestObjectFactory.newConfigOptions(
        ImmutableMap.<String, String>of("feed.maxUrls", "3"));
    addDocuments(docEntries);

    DocumentTraverser traverser = new DocumentTraverser(options);
    assertTrue(String.valueOf(docEntries.length), docEntries.length > 1);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();

    Checkpoint checkpoint = new Checkpoint("incremental", new Date(),
        new Id("AAAAAAAA-4000-0000-0000-000000000000"));
    assertCheckpointEquals(checkpoint, traverser.getDocIds(checkpoint, pusher));
    List<Record> docList = pusher.getRecords();
    assertCheckpointEquals(checkpoint, getDeleteCheckpoint(docList));
    assertEquals(ImmutableList.of(
        new Record.Builder(newDocId(checkpoint))
            .setDeleteFromIndex(true)
            .build()),
        pusher.getRecords());
  }

  @Test
  public void testGetModifiedDocIds_someNew() throws Exception {
    options = TestObjectFactory.newConfigOptions();
    addDocuments(docEntries);

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    Checkpoint checkpoint = new Checkpoint("incremental", new Date(),
        new Id("AAAAAAAA-4000-0000-0000-000000000000"));

    Date now = new Date();
    String newDate = dateFormatter.format(now);
    String[][] newEntries = {
      { "AAAAAAAA-5000-0000-0000-000000000000", newDate },
      { "AAAAAAAA-6000-0000-0000-000000000000", newDate },
      { "AAAAAAAA-7000-0000-0000-000000000000", newDate },
      { "AAAAAAAA-8000-0000-0000-000000000000", newDate }
    };
    addDocuments(newEntries);

    Checkpoint newCheckpoint = traverser.getDocIds(checkpoint, pusher);
    assertCheckpointEquals(new Checkpoint("incremental", now,
        new Id("{AAAAAAAA-8000-0000-0000-000000000000}")), newCheckpoint);
    assertEquals(ImmutableList.of(
        new Record.Builder(
            newDocId(new Id("{AAAAAAAA-5000-0000-0000-000000000000}")))
            .setCrawlImmediately(true).build(),
        new Record.Builder(
            newDocId(new Id("{AAAAAAAA-6000-0000-0000-000000000000}")))
            .setCrawlImmediately(true).build(),
        new Record.Builder(
            newDocId(new Id("{AAAAAAAA-7000-0000-0000-000000000000}")))
            .setCrawlImmediately(true).build(),
        new Record.Builder(
            newDocId(new Id("{AAAAAAAA-8000-0000-0000-000000000000}")))
            .setCrawlImmediately(true).build(),
        new Record.Builder(newDocId(newCheckpoint))
            .setCrawlImmediately(true).build(),
        new Record.Builder(newDocId(checkpoint))
            .setDeleteFromIndex(true)
            .build()),
        pusher.getRecords());

    pusher.reset();
    assertEquals(newCheckpoint, traverser.getDocIds(newCheckpoint, pusher));
    assertEquals(ImmutableList.of(
        new Record.Builder(newDocId(newCheckpoint))
            .setDeleteFromIndex(true)
            .build()),
        pusher.getRecords());
  }

  @Test
  public void testContentSize() throws Exception {
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 32, true);
  }

  @Test
  public void testContentSize_tooLarge() throws Exception {
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 1024 * 3L, false);
  }

  @Test
  public void testContentSize_mimeTypeIgnored() throws Exception {
    testMimeTypeAndContentSize("video/3gpp", 1024 * 1024 * 100, true);
  }

  private void testMimeTypeAndContentSize(String mimeType, double size,
      boolean expectNotNull) throws Exception {
    MockObjectStore os = getObjectStore();
    mockDocument(os, "AAAAAAA1", DOCUMENT_TIMESTAMP, RELEASED, size, mimeType);

    DocumentTraverser traverser = new DocumentTraverser(options);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Id> docList = getIds(pusher.getRecords());

    Id id = docList.get(0);
    Request request = new MockRequest(newDocId(id));
    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(id, request, response);

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
    mockDocument(os, id, DOCUMENT_TIMESTAMP, RELEASED, 42d, "text/plain",
        TestObjectFactory.getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId);
    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(new Id(id), request, response);

    assertEquals(
        new Metadata(
            ImmutableMap.of(PropertyNames.ID, id.substring(1, id.length() - 1),
                PropertyNames.DATE_LAST_MODIFIED, DOCUMENT_DATE,
                PropertyNames.CONTENT_SIZE, "42.0",
                PropertyNames.MIME_TYPE, "text/plain")
            .entrySet()),
        response.getMetadata());

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

  @Test
  public void testGetDocContent_markAllDocsPublic() throws Exception {
    options = TestObjectFactory.newConfigOptions(
        ImmutableMap.<String, String>of (
            "adaptor.markAllDocsAsPublic", "true"));

    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();
    mockDocument(os, id, DOCUMENT_TIMESTAMP, RELEASED, 42d, "text/plain",
        TestObjectFactory.getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    RecordingResponse response = new RecordingResponse(baos);
    traverser.getDocContent(new Id(id), request, response);

    assertEquals(
        new Metadata(
            ImmutableMap.of(PropertyNames.ID, id.substring(1, id.length() - 1),
                PropertyNames.DATE_LAST_MODIFIED, DOCUMENT_DATE,
                PropertyNames.CONTENT_SIZE, "42.0",
                PropertyNames.MIME_TYPE, "text/plain")
            .entrySet()),
        response.getMetadata());

    byte[] actualContent = baos.toByteArray();
    assertEquals("sample content", new String(actualContent, UTF_8));

    assertFalse(response.isSecure());
    assertNull(response.getAcl());
    assertTrue(response.getNamedResources().isEmpty());
  }

  @Test
  public void testGetDocContent_notModified() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();
    mockDocument(os, id, DOCUMENT_TIMESTAMP, RELEASED, 42d, "text/plain",
        TestObjectFactory.getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT));

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId, new Date());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    RecordingResponse response = new RecordingResponse(baos);
    traverser.getDocContent(new Id(id), request, response);

    assertEquals(
        new Metadata(
            ImmutableMap.of(PropertyNames.ID, id.substring(1, id.length() - 1),
                PropertyNames.DATE_LAST_MODIFIED, DOCUMENT_DATE,
                PropertyNames.CONTENT_SIZE, "42.0",
                PropertyNames.MIME_TYPE, "text/plain")
            .entrySet()),
        response.getMetadata());

    assertNotNull(response.getAcl());
    assertFalse(response.getNamedResources().isEmpty());
    assertEquals(RecordingResponse.State.NO_CONTENT, response.getState());
    assertEquals(0, baos.size());
  }

  @Test
  public void testGetDocContent_notFound_fetchObjects() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId, new Date());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    RecordingResponse response = new RecordingResponse(baos);
    traverser.getDocContent(new Id(id), request, response);

    assertNull(response.getAcl());
    assertTrue(response.getNamedResources().isEmpty());
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
    assertEquals(0, baos.size());
  }

  @Test
  public void testGetDocContent_notFound_refresh() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();
    mockDocumentNotFound(os, id);

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId, new Date());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    RecordingResponse response = new RecordingResponse(baos);
    traverser.getDocContent(new Id(id), request, response);

    assertNull(response.getAcl());
    assertTrue(response.getNamedResources().isEmpty());
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
    assertEquals(0, baos.size());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testGetDocContent_activeMarkings() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    String markingId1 = "{AAAAAAAA-0001-0000-0000-000000000000}";
    String markingId2 = "{AAAAAAAA-0002-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = getObjectStore();

    mockDocument(os, id, DOCUMENT_TIMESTAMP, RELEASED,
        1000d, "text/plain",
        TestObjectFactory.getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT),
        new ActiveMarkingListMock(
            mockActiveMarking("marking1", markingId1,
                TestObjectFactory.getMarkingPermissions(),
                AccessLevel.FULL_CONTROL_AS_INT),
            mockActiveMarking("marking2", markingId2,
                TestObjectFactory.getMarkingPermissions(),
                AccessRight.READ_AS_INT)));

    DocumentTraverser traverser = new DocumentTraverser(options);
    Request request = new MockRequest(docId);
    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(new Id(id), request, response);

    assertEquals(
        new Metadata(
            ImmutableMap.of(PropertyNames.ID, id.substring(1, id.length() - 1),
                PropertyNames.DATE_LAST_MODIFIED, DOCUMENT_DATE,
                PropertyNames.CONTENT_SIZE, "1000.0",
                PropertyNames.MIME_TYPE, "text/plain")
            .entrySet()),
        response.getMetadata());

    assertEquals("text/plain", response.getContentType());
    byte[] actualContent =
        ((ByteArrayOutputStream) response.getOutputStream()).toByteArray();
    assertEquals("sample content", new String(actualContent, UTF_8));

    Acl acl = response.getAcl();
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals("TMPL", acl.getInheritFromFragment());

    acl = response.getNamedResources().get("TMPL");
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(InheritanceType.CHILD_OVERRIDES, acl.getInheritanceType());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals("FLDR", acl.getInheritFromFragment());

    acl = response.getNamedResources().get("FLDR");
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(InheritanceType.CHILD_OVERRIDES, acl.getInheritanceType());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals(markingFragment(markingId2), acl.getInheritFromFragment());

    acl = response.getNamedResources().get(markingFragment(markingId2));
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(InheritanceType.AND_BOTH_PERMIT, acl.getInheritanceType());
    assertEquals(docId, acl.getInheritFrom());
    assertEquals(markingFragment(markingId1), acl.getInheritFromFragment());

    acl = response.getNamedResources().get(markingFragment(markingId1));
    assertFalse(acl.getPermitUsers().toString(),
        acl.getPermitUsers().isEmpty());
    assertEquals(InheritanceType.AND_BOTH_PERMIT, acl.getInheritanceType());
    assertEquals(null, acl.getInheritFrom());
    assertEquals(null, acl.getInheritFromFragment());
  }

  private String markingFragment(String id) {
    return "MARK" + percentEscape(new Id(id));
  }

  /**
   * Creates an object set of documents.
   *
   * @param entries an array of arrays of IDs and timestamps
   */
  private void addDocuments(String[][] entries) {
    MockObjectStore os = getObjectStore();
    for (String[] entry : entries) {
      mockDocument(os, entry[0], entry[1], RELEASED, 42d);
    }
  }

  private void assertCheckpointEquals(Checkpoint expected,
      Checkpoint actual) {
    assertEquals(expected.type, actual.type);
    assertEquals(expected.timestamp, actual.timestamp);
    assertEquals(expected.guid, actual.guid);
  }

  /** Tests that including nothing explicitly fetches everything. */
  @Test
  public void testGetDocumentPropertyFilter_emptyEmpty() {
    PropertyFilter filter =
        DocumentTraverser.getDocContentPropertyFilter(
            ImmutableSet.<String>of(),
            ImmutableSet.<String>of());
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeTypes().length)));
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeProperties().length)));
    assertEquals(filter.toString(), 0, filter.getExcludeProperties().length);
  }

  /** Tests that excluding properties still fetches everything. */
  @Test
  public void testGetDocumentPropertyFilter_emptyNonempty() {
    PropertyFilter filter =
        DocumentTraverser.getDocContentPropertyFilter(
            ImmutableSet.<String>of(),
            ImmutableSet.of(PropertyNames.DATE_CREATED, PropertyNames.ID));
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeTypes().length)));
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeProperties().length)));
    assertEquals(filter.toString(), 1, filter.getExcludeProperties().length);
    assertEquals(PropertyNames.DATE_CREATED, filter.getExcludeProperties()[0]);
  }

  /** Tests that included properties are added to the filter. */
  @Test
  public void testGetDocumentPropertyFilter_nonemptyEmpty() {
    PropertyFilter filter =
        DocumentTraverser.getDocContentPropertyFilter(
            // This should be something that we don't fetch by default.
            ImmutableSet.of(PropertyNames.DATE_CREATED),
            ImmutableSet.<String>of());
    assertEquals(filter.toString(), 0, filter.getIncludeTypes().length);
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeProperties().length)));
    boolean found = false;
    for (FilterElement element : filter.getIncludeProperties()) {
      if (PropertyNames.DATE_CREATED.equals(element.getValue())) {
        found = true;
        break;
      }
    }
    assertTrue(filter.toString(), found);
    assertEquals(filter.toString(), 0, filter.getExcludeProperties().length);
  }

  /** Tests that we can exclude included properties, but not builtin ones. */
  @Test
  public void testGetDocumentPropertyFilter_nonemptyNonempty() {
    PropertyFilter filter =
        DocumentTraverser.getDocContentPropertyFilter(
            // This should be something that we don't fetch by default.
            ImmutableSet.of(PropertyNames.DATE_CREATED),
            ImmutableSet.of(PropertyNames.DATE_CREATED, PropertyNames.ID));
    assertEquals(filter.toString(), 0, filter.getIncludeTypes().length);
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeProperties().length)));
    for (FilterElement element : filter.getIncludeProperties()) {
      if (PropertyNames.DATE_CREATED.equals(element.getValue())) {
        fail("Found DateCreated in " + filter);
      }
    }
    assertEquals(filter.toString(), 0, filter.getExcludeProperties().length);
  }

  /** Tests that included required properties are not added to the filter. */
  @Test
  public void testGetDocumentPropertyFilter_requiredEmpty() {
    PropertyFilter filter =
        DocumentTraverser.getDocContentPropertyFilter(
            // This should be something that we fetch by default.
            ImmutableSet.of(PropertyNames.ID),
            ImmutableSet.<String>of());
    assertEquals(filter.toString(), 0, filter.getIncludeTypes().length);
    assertThat(filter.toString(), 0,
        not(equalTo(filter.getIncludeProperties().length)));
    for (FilterElement element : filter.getIncludeProperties()) {
      assertThat(filter.toString(), "", not(equalTo(element.getValue())));
      if (PropertyNames.ID.equals(element.getValue())) {
        fail("Found Id in " + filter);
      }
    }
    assertEquals(filter.toString(), 0, filter.getExcludeProperties().length);
  }
}
