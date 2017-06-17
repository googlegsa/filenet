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

import static com.google.enterprise.adaptor.DocIdPusher.Record;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.InvalidConfigurationException;
import com.google.enterprise.adaptor.testing.RecordingDocIdPusher;
import com.google.enterprise.adaptor.testing.RecordingResponse;

import com.filenet.api.util.Id;
import com.filenet.api.util.UserContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import javax.security.auth.Subject;

/** Unit tests for FileNetAdaptor */
public class FileNetAdaptorTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final TimeZone defaultTimeZone = TimeZone.getDefault();

  private FileNetAdaptor adaptor;
  private AdaptorContext context;
  private Config config;

  @Before
  public void setUp() throws Exception {
    FileNetProxies proxies = new FileNetProxies();
    adaptor = new FileNetAdaptor(proxies);
    context = ProxyAdaptorContext.getInstance();
    config = context.getConfig();
    adaptor.initConfig(config);
    config.overrideKey("filenet.contentEngineUrl", "http://test.example.com");
    config.overrideKey("filenet.username", "test");
    config.overrideKey("filenet.password", "password");
    config.overrideKey("filenet.objectStore", "ObjectStore");
  }

  @After
  public void tearDown() throws Exception {
    TimeZone.setDefault(defaultTimeZone);
  }

  @Test
  public void testInit() throws Exception {
    adaptor.init(context);
  }

  /** Get the RecordingDocIdPusher from the ProxyAdaptorContext. */
  private RecordingDocIdPusher getContextPusher() {
    return (RecordingDocIdPusher) context.getDocIdPusher();
  }

  private static DocId newDocId(Checkpoint checkpoint) {
    return new DocId("pseudo/" + checkpoint);
  }

  private static DocId newDocId(String id) {
    return new DocId("guid/" + id);
  }

  @Test
  public void testGetConnection() throws Exception {
    // Mock sensitiveValueDecoder uppercases the value.
    Subject subject = new Subject(true, ImmutableSet.<Principal>of(),
        ImmutableSet.of("test"), ImmutableSet.of("PASSWORD"));
    assertFalse(subject.equals(UserContext.get().getSubject()));
    adaptor.init(context);
    try (Connection connection = adaptor.getConnection()) {
      assertEquals("http://test.example.com",
          connection.getConnection().getURI());
      assertTrue(subject.equals(UserContext.get().getSubject()));
    }
    assertFalse(subject.equals(UserContext.get().getSubject()));
  }

  @Test
  public void testCheckpoint_ctor_nullCheckpoint() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("checkpoint may not be null");
    new Checkpoint(null);
  }

  @Test
  public void testCheckpoint_ctor_nullType() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("type may not be null");
    new Checkpoint(null, new Date(),
        new Id("{AAAAAAAA-0000-0000-0000-000000000000}"));
  }

  @Test
  public void testCheckpoint_ctor_invalidCheckpoint() {
    thrown.expect(IllegalArgumentException.class);
    new Checkpoint("foo=bar");
  }

  @Test
  public void testCheckpoint_ctor_shortCheckpoint() {
    Checkpoint checkpoint = new Checkpoint("type=document");
    assertEquals("document", checkpoint.type);
    assertNull(checkpoint.timestamp);
    assertNull(checkpoint.guid);
  }

  @Test
  public void testCheckpoint_ctor_longCheckpoint() throws Exception {
    SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    String timestamp = dateFmt.format(new Date());

    Checkpoint checkpoint = new Checkpoint("type=document;"
        + "timestamp=" + timestamp
        + ";guid={AAAAAAAA-0000-0000-0000-000000000000}");
    assertEquals("document", checkpoint.type);
    assertEquals(timestamp, checkpoint.timestamp);
    assertEquals("{AAAAAAAA-0000-0000-0000-000000000000}", checkpoint.guid);
  }

  @Test
  public void testCheckpoint_ctor_threeArgs() throws Exception {
    SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    String type = "document";
    Date now = new Date();
    Id id = new Id("{AAAAAAAA-0000-0000-0000-000000000000}");

    Checkpoint checkpoint = new Checkpoint(type, now, id);
    assertEquals(type, checkpoint.type);
    String nowStr = dateFmt.format(now);
    String expected = nowStr.substring(0, nowStr.length() - 2) + ":"
        + nowStr.substring(nowStr.length() - 2);
    assertEquals(expected, checkpoint.timestamp);
    assertEquals(id.toString(), checkpoint.guid);
  }

  @Test
  public void testCheckpoint_toString_shortCheckpoint() {
    String checkpointStr = "type=document";
    Checkpoint checkpoint = new Checkpoint(checkpointStr);
    assertEquals(checkpointStr, checkpoint.toString());
  }

  @Test
  public void testCheckpoint_toString_longCheckpoint() throws Exception {
    SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    Date now = new Date();
    String checkpointStr = "type=document;timestamp=" + dateFmt.format(now)
       + ";guid={AAAAAAAA-0000-0000-0000-000000000000}";
    Checkpoint checkpoint = new Checkpoint(checkpointStr);
    assertEquals(checkpointStr, checkpoint.toString());
  }

  // These tests of getQueryTimeString are adapted from the v3 FileUtilsTest.
  private void testCheckpoint_getQueryTimeString(String tzStr,
      String dateUnderTest, String expected) throws Exception {
    SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    TimeZone.setDefault(TimeZone.getTimeZone(tzStr));
    assertEquals(expected,
        Checkpoint.getQueryTimeString(dateFmt.parse(dateUnderTest)));
  }

  @Test
  public void testCheckpoint_getQueryTimeString_West() throws Exception {
    testCheckpoint_getQueryTimeString("GMT-0800",
        "2013-04-30T02:00:00.392-0800",
        "2013-04-30T02:00:00.392-08:00");
  }

  @Test
  public void testCheckpoint_getQueryTimeString_Utc() throws Exception {
    testCheckpoint_getQueryTimeString("GMT",
        "2013-04-30T10:00:00.392+0000",
        "2013-04-30T10:00:00.392+00:00");
  }

  @Test
  public void testCheckpoint_getQueryTimeString_East() throws Exception {
    testCheckpoint_getQueryTimeString("GMT+0400",
        "2013-04-30T14:00:00.392+0400",
        "2013-04-30T14:00:00.392+04:00");
  }

  @Test
  public void testInit_maxFeedUrlsTooSmall() throws Exception {
    config.overrideKey("feed.maxUrls", "1");
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("feed.maxUrls must be greater than 2");
    adaptor.init(context);
  }

  @Test
  public void testGetDocIds() throws Exception {
    RecordingDocIdPusher pusher = getContextPusher();
    config.overrideKey("feed.maxUrls", "5");
    adaptor.init(context);
    adaptor.getDocIds(pusher);

    assertEquals(ImmutableList.of(
        new Record.Builder(newDocId(new Checkpoint("type=document")))
            .setCrawlImmediately(true).build()),
        pusher.getRecords());
  }

  @Test
  public void testGetDocContent_invalidDocId1() throws Exception {
    adaptor.init(context);

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(new MockRequest(new DocId("foo")),
        response);
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
  }

  @Test
  public void testGetDocContent_invalidDocId2() throws Exception {
    adaptor.init(context);

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(new MockRequest(new DocId("foo/bar")),
        response);
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
  }

  @Test
  public void testGetDocContent_invalidDocId3() throws Exception {
    adaptor.init(context);

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(
        new MockRequest(newDocId(new Checkpoint("type=foo"))),
        response);
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
  }

  @Test
  public void testGetDocContent_invalidGuid() throws Exception {
    adaptor.init(context);

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(new MockRequest(newDocId("guid/invalid-guid")),
        response);
    assertEquals(RecordingResponse.State.NOT_FOUND, response.getState());
  }

  @Test
  public void testGetDocContent_shortCheckpoint() throws Exception {
    config.overrideKey("feed.maxUrls", "5");
    adaptor.init(context);

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(
        new MockRequest(newDocId(new Checkpoint("type=document"))),
        response);

    List<Record> actual = getContextPusher().getRecords();
    // Assert that the pushed DocIds match.
    assertEquals(ImmutableList.of(
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000001}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000002}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000003}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000004}"))
            .build()),
        actual.subList(0, actual.size() - 1));

    // Now check the continuation record.
    Record continuation = actual.get(actual.size() - 1);
    String docid = continuation.getDocId().getUniqueId();
    assertTrue(docid, docid.startsWith("pseudo/"));
    Checkpoint checkpoint =
        new Checkpoint(docid.substring(docid.indexOf('/') + 1));
    assertEquals("document", checkpoint.type);
    assertEquals("{AAAAAAAA-0000-0000-0000-000000000004}", checkpoint.guid);
  }

  @Test
  public void testGetDocContent_longCheckpoint() throws Exception {
    config.overrideKey("feed.maxUrls", "5");
    adaptor.init(context);

    Date timestamp = new Date();
    Checkpoint startCheckpoint = new Checkpoint("document", timestamp,
        new Id("{AAAAAAAA-0000-0000-0000-000000000004}"));

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(
        new MockRequest(newDocId(startCheckpoint)), response);

    List<Record> actual = getContextPusher().getRecords();
    // Assert that the pushed DocIds match.
    assertEquals(ImmutableList.of(
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000005}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000006}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000007}"))
            .build(),
        new Record.Builder(newDocId("{AAAAAAAA-0000-0000-0000-000000000008}"))
            .build()),
        actual.subList(0, actual.size() - 1));

    // Now check the continuation record.
    Record continuation = actual.get(actual.size() - 1);
    String docid = continuation.getDocId().getUniqueId();
    assertTrue(docid, docid.startsWith("pseudo/"));
    Checkpoint checkpoint =
        new Checkpoint(docid.substring(docid.indexOf('/') + 1));
    assertEquals("document", checkpoint.type);
    assertEquals("{AAAAAAAA-0000-0000-0000-000000000008}", checkpoint.guid);
  }

  @Test
  public void testGetDocContent_endOfTraversal() throws Exception {
    config.overrideKey("feed.maxUrls", "5");
    adaptor.init(context);

    Date timestamp = new Date();
    Checkpoint startCheckpoint = new Checkpoint("document", timestamp,
        new Id("{AAAAAAAA-0000-0000-0000-000000010000}"));

    RecordingResponse response = new RecordingResponse();
    adaptor.getDocContent(
        new MockRequest(newDocId(startCheckpoint)), response);
    assertEquals(ImmutableList.of(), getContextPusher().getRecords());
  }

  @Test
  public void testGetDocContent_guidDocId() throws Exception {
    adaptor.init(context);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    RecordingResponse response = new RecordingResponse(baos);
    adaptor.getDocContent(
        new MockRequest(newDocId("{AAAAAAAA-0000-0000-0000-000000000004}")),
        response);
    assertEquals("text/plain", response.getContentType());
    assertEquals("Hello from document {AAAAAAAA-0000-0000-0000-000000000004}",
        baos.toString("UTF-8"));
  }
}