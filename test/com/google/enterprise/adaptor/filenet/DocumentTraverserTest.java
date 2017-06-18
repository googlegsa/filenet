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
import static com.google.enterprise.adaptor.filenet.ObjectMocks.newObjectStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.IndependentObjectSetMock;
import com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;
import com.google.enterprise.adaptor.testing.RecordingDocIdPusher;
import com.google.enterprise.adaptor.testing.RecordingResponse;

import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.core.IndependentObject;
import com.filenet.api.util.Id;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Tests for DocumentTraverser. */
public class DocumentTraverserTest extends TraverserFactoryFixture {
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

  private FileConnector connec;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    connec = TestObjectFactory.newFileConnector();
  }

  private DocumentTraverser getObjectUnderTest(MockObjectStore os,
      IndependentObjectSet docSet) {
    return getDocumentTraverser(connec, os, docSet,
        new Capture<String>(CaptureType.NONE));
  }

  @Test
  public void testGetDocumentList_empty() throws Exception {
    MockObjectStore objectStore = newObjectStore();
    DocumentTraverser traverser =
        getObjectUnderTest(objectStore, new EmptyObjectSet());
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);
    assertEquals(pusher.getDocIds().toString(), 0, pusher.getDocIds().size());
    verifyAll();
  }

  @Test
  public void testGetDocumentList_nonEmpty() throws Exception {
    MockObjectStore objectStore = newObjectStore();
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    Date now = new Date();
    String lastModified = dateFormatter.format(now);
    IndependentObject doc = mockDocument(objectStore, id, lastModified, true,
        getPermissions(PermissionSource.SOURCE_DIRECT));
    DocumentTraverser traverser = getObjectUnderTest(objectStore,
        new IndependentObjectSetMock(ImmutableList.of(doc)));
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);

    assertEquals(ImmutableList.of(new Id(id)), getIds(pusher.getDocIds()));
    String checkpoint = getCheckpoint(pusher.getDocIds());
    assertTrue(checkpoint, checkpoint.contains(id));
    assertTrue(checkpoint,
        checkpoint.contains(Checkpoint.getQueryTimeString(now)));
    verifyAll();
  }

  private ImmutableList<Id> getIds(List<DocId> docList) {
    ImmutableList.Builder<Id> builder = ImmutableList.builder();
    for (DocId docId : docList) {
      String s = docId.getUniqueId();
      if (s.startsWith("guid/")) {
        builder.add(new Id(s.substring(5)));
      }
    }
    return builder.build();
  }

  private String getCheckpoint(List<DocId> docList) {
    String checkpoint = null;
    for (DocId docId : docList) {
      String s = docId.getUniqueId();
      if (s.startsWith("pseudo/")) {
        assertNull(checkpoint);
        checkpoint = s.substring(7);
      }
    }
    return checkpoint;
  }

  @Test
  public void testGetDocumentList_initialCheckpoint() throws Exception {
    connec.setDelete_additional_where_clause("and 1=1");

    MockObjectStore objectStore = newObjectStore();
    Capture<String> capture = new Capture<>(CaptureType.ALL);
    DocumentTraverser traverser = getDocumentTraverser(connec, objectStore,
        new EmptyObjectSet(), capture);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);
    assertEquals(pusher.getDocIds().toString(), 0, pusher.getDocIds().size());
    assertTrue(capture.toString(), capture.hasCaptured());
    List<String> queries = capture.getValues();
    assertEquals(queries.toString(), 1, queries.size());

    // Smoke test the executed queries.
    SearchMock search = new SearchMock();
    for (String query : queries) {
      search.executeSql(query);
    }

    // The document query should be unconstrained.
    assertFalse(queries.get(0),
        queries.get(0).contains(" AND ((DateLastModified="));
    verifyAll();
  }

  @Test
  public void testGetCheckpointClause() throws Exception {
    String expectedId = "{AAAAAAAA-0000-0000-0000-000000000000}";
    Date expectedDate = new Date();
    String expectedDateString = Checkpoint.getQueryTimeString(expectedDate);

    DocumentTraverser traverser =
        new DocumentTraverser(null, null, null, connec);
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
  public void testNullObjectSet_nullDocuments() throws Exception {
    MockObjectStore os = newObjectStore();
    DocumentTraverser traverser = getObjectUnderTest(os, null);
    // We're expecting a NullPointerException, but that leaves
    // unverified mocks, so an AssertionError is also thrown. So we
    // get a MultipleFailureException, but the version of JUnit we
    // compile against doesn't know about that (I think we're getting
    // a newew version of JUnit from Cobertura at runtime, but haven't
    // verified that).
    thrown.expect(Exception.class);
    traverser.getDocIds(CHECKPOINT, new RecordingDocIdPusher());
  }

  @Test
  public void testGetDocIds_noResults() throws Exception {
    MockObjectStore os = newObjectStore();
    DocumentTraverser traverser = getObjectUnderTest(os, new EmptyObjectSet());
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<DocId> docList = pusher.getDocIds();
    assertEquals(ImmutableList.of(), docList);
    verifyAll();
  }

  @Test
  public void testGetDocIds_emptyCheckpoint() throws Exception {
    MockObjectStore os = newObjectStore();
    IndependentObjectSet docSet = getDocuments(os, docEntries, true);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = getObjectUnderTest(os, docSet);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(EMPTY_CHECKPOINT, pusher);
    List<DocId> docList = pusher.getDocIds();
    for (Id id : getIds(docList)) {
      assertTrue(os.containsObject(ClassNames.DOCUMENT, id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-4000-0000-0000-000000000000}");
    verifyAll();
  }

  @Test
  public void testGetDocIds_checkpoint() throws Exception {
    MockObjectStore os = newObjectStore();
    IndependentObjectSet docSet = getDocuments(os, docEntries, true);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = getObjectUnderTest(os, docSet);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<DocId> docList = pusher.getDocIds();
    for (Id id : getIds(docList)) {
      assertTrue(os.containsObject(ClassNames.DOCUMENT, id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(docEntries.length, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-4000-0000-0000-000000000000}");
    verifyAll();
  }

  // TODO(jlacey): This is not interesting against the current mocks,
  // but it could become so with H2-backed proxies, or even made so
  // with better mocks.
  @Test
  public void testGetDocIds_monotonicDates() throws Exception {
    MockObjectStore os = newObjectStore();
    IndependentObjectSet docSet = getDocuments(os, docEntries, true);

    DocumentTraverser traverser = getObjectUnderTest(os, docSet);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Id> docList = getIds(pusher.getDocIds());
    assertFalse(docList.isEmpty());

    int counter = 0;
    Date prevDate = new Date(0L);
    for (Id id : docList) {
      assertTrue(os.containsObject(ClassNames.DOCUMENT, id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      Date thisDate = response.getLastModified();
      assertTrue("Previous date " + prevDate + " is after " + thisDate,
          prevDate.compareTo(thisDate) <= 0);
      prevDate = thisDate;
      counter++;
    }
    assertEquals(docEntries.length, counter);
    verifyAll();
  }

  @Test
  public void testGetDocIds_batchHint() throws Exception {
    MockObjectStore os = newObjectStore();
    IndependentObjectSet docSet = getDocuments(os, docEntries, true);

    int counter = 0;
    Date lastModified = null;
    DocumentTraverser traverser = getObjectUnderTest(os, docSet);
    traverser.setBatchHint(1);
    assertTrue(String.valueOf(docEntries.length), docEntries.length > 1);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<DocId> docList = pusher.getDocIds();
    for (Id id : getIds(docList)) {
      assertTrue(os.containsObject(ClassNames.DOCUMENT, id));
      RecordingResponse response = new RecordingResponse();
      traverser.getDocContent(id, null, response);
      lastModified = response.getLastModified();
      counter++;
    }
    assertEquals(1, counter);

    assertEquals(CHECKPOINT_TIMESTAMP, getQueryTimeString(lastModified));
    assertCheckpointEquals(getCheckpoint(docList),
        CHECKPOINT_TIMESTAMP, "{AAAAAAAA-1000-0000-0000-000000000000}");
    verifyAll();
  }

  @Test
  public void testCheckpointWithoutNextDocument() throws Exception {
    MockObjectStore os = newObjectStore();
    DocumentTraverser traverser = getObjectUnderTest(os, new EmptyObjectSet());
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<DocId> docList = pusher.getDocIds();

    assertEquals(ImmutableList.of(), docList);
    verifyAll();
  }

  @Test
  public void testMimeTypesAndSizes() throws Exception {
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 32, true);
    testMimeTypeAndContentSize("text/plain", 1024 * 1024 * 1024 * 3L, false);
    testMimeTypeAndContentSize("video/3gpp", 1024 * 1024 * 100, true);
  }

  private void testMimeTypeAndContentSize(String mimeType, double size,
      boolean expectNotNull) throws Exception {
    MockObjectStore os = newObjectStore();
    IndependentObject doc1 = mockDocument(os,
        "AAAAAAA1", DOCUMENT_TIMESTAMP, false, size, mimeType);
    IndependentObjectSet docSet =
        new IndependentObjectSetMock(ImmutableList.of(doc1));

    DocumentTraverser traverser = getObjectUnderTest(os, docSet);
    RecordingDocIdPusher pusher = new RecordingDocIdPusher();
    traverser.getDocIds(CHECKPOINT, pusher);
    List<Id> docList = getIds(pusher.getDocIds());

    RecordingResponse response = new RecordingResponse();
    traverser.getDocContent(docList.get(0), null, response);

    int contentSize =
        ((ByteArrayOutputStream) response.getOutputStream()).size();
    assertEquals("Content size: " + contentSize,
        expectNotNull, contentSize > 0);
    verifyAll();
  }

  @Test
  public void testGetDocContent() throws Exception {
    String id = "{AAAAAAAA-0000-0000-0000-000000000000}";
    DocId docId = newDocId(new Id(id));
    MockObjectStore os = newObjectStore();
    IndependentObject doc = mockDocument(os, id, DOCUMENT_TIMESTAMP, true,
        getPermissions(
            PermissionSource.SOURCE_DIRECT,
            PermissionSource.SOURCE_TEMPLATE,
            PermissionSource.SOURCE_PARENT));

    DocumentTraverser traverser = new DocumentTraverser(null, null, os, connec);
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
   * @param releasedVersion if the documents should be released versions
   */
  private IndependentObjectSet getDocuments(MockObjectStore os,
      String[][] entries, boolean releasedVersion) {
    return getObjects(os, entries, releasedVersion);
  }

  /**
   * Creates an object set of new objects.
   *
   * @param os the object store to create the objects in
   * @param entries an array of arrays of IDs and timestamps
   * @param releasedVersion if the objects should refer to released versions
   */
  private IndependentObjectSet getObjects(MockObjectStore os,
      String[][] entries, boolean releasedVersion) {
    List<IndependentObject> objectList = new ArrayList<>(entries.length);
    for (String[] entry : entries) {
      objectList.add(mockDocument(os, entry[0], entry[1], releasedVersion));
    }
    return new IndependentObjectSetMock(objectList);
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
