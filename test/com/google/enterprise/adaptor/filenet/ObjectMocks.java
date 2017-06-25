// Copyright 2015 Google Inc. All Rights Reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.AccessPermissionListMock;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.ActiveMarkingListMock;
import com.google.enterprise.adaptor.filenet.FileNetProxies.MockObjectStore;

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.collection.ActiveMarkingList;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.constants.VersionStatus;
import com.filenet.api.core.Document;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.events.DeletionEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.property.PropertyDateTime;
import com.filenet.api.property.PropertyFloat64;
import com.filenet.api.property.PropertyId;
import com.filenet.api.property.PropertyString;
import com.filenet.api.security.ActiveMarking;
import com.filenet.api.security.Marking;
import com.filenet.api.util.Id;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * These mocks make the assumption that the VersionSeries ID is the
 * same as the Document ID. DeletionEvents have a separate ID.
 */
class ObjectMocks {
  private static final SimpleDateFormat dateFormatter =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

  private static Date parseTime(String timeStr) {
    try {
      return dateFormatter.parse(timeStr);
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  private static final String ZERO_ID = "00000000-0000-0000-0000-000000000000";

  /**
   * Create a new {@code Id} from a possibly truncated string. The
   * string is zero-extended to the proper length, 32 hex digits or 16 bytes.
   *
   * @param guid a full or partial GUID string
   */
  public static Id newId(String guid) {
    if (guid.startsWith("{")) {
      guid = guid.substring(1, guid.length() - 1);
    }
    if (guid.length() > ZERO_ID.length()) {
      throw new AssertionError("GUID is too long: " + guid);
    }
    return new Id(guid + ZERO_ID.substring(guid.length()));
  }

  /** Gets a basic Document. */
  public static Document mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, boolean isReleasedVersion) {
    return mockDocument(objectStore, guid, timeStr, isReleasedVersion,
        null, null, new AccessPermissionListMock());
  }

  /** Gets a Document with ContentSize and MimeType properties. */
  public static Document mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, boolean isReleasedVersion,
      Double contentSize, String mimeType) {
    return mockDocument(objectStore, guid, timeStr, isReleasedVersion,
        contentSize, mimeType, new AccessPermissionListMock());
  }

  /** Gets a Document with a Permissions property. */
  public static Document mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, boolean isReleasedVersion,
      AccessPermissionList perms) {
    return mockDocument(objectStore, guid, timeStr, isReleasedVersion,
        null, null, perms);
  }

  /**
   * Gets a Document with ContentSize, MimeType, and Permissions properties.
   */
  public static Document mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, boolean isReleasedVersion,
      Double contentSize, String mimeType, AccessPermissionList perms) {
    return mockDocument(objectStore, guid, timeStr, isReleasedVersion,
         contentSize, mimeType, perms, new ActiveMarkingListMock());
  }

  /**
   * Gets a Document with ContentSize, MimeType, ActiveMarkings, and
   * Permissions properties.
   */
  public static Document mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, boolean isReleasedVersion,
      Double contentSize, String mimeType, AccessPermissionList perms,
      ActiveMarkingList activeMarkings) {
    VersionSeries vs = createMock(VersionSeries.class);
    expect(vs.get_Id()).andStubReturn(newId(guid));

    Property[] props = new Property[4];
    props[0] = createMock(PropertyId.class);
    expect(props[0].getPropertyName()).andStubReturn(PropertyNames.ID);
    expect(props[0].getIdValue()).andStubReturn(newId(guid));
    replay(props[0]);
    props[1] = createMock(PropertyDateTime.class);
    expect(props[1].getPropertyName())
        .andStubReturn(PropertyNames.DATE_LAST_MODIFIED);
    expect(props[1].getDateTimeValue()).andStubReturn(parseTime(timeStr));
    replay(props[1]);
    props[2] = createMock(PropertyString.class);
    expect(props[2].getPropertyName()).andStubReturn(PropertyNames.MIME_TYPE);
    expect(props[2].getStringValue()).andStubReturn(mimeType);
    replay(props[2]);
    props[3] = createMock(PropertyFloat64.class);
    expect(props[3].getPropertyName())
        .andStubReturn(PropertyNames.CONTENT_SIZE);
    expect(props[3].getFloat64Value()).andStubReturn(contentSize);
    replay(props[3]);
    Properties properties = createMock(Properties.class);
    expect(properties.toArray()).andStubReturn(props);

    Document doc = createMock(Document.class);
    expect(doc.get_Id()).andStubReturn(newId(guid));
    expect(doc.get_VersionSeries()).andStubReturn(vs);
    expect(doc.get_DateLastModified()).andStubReturn(parseTime(timeStr));
    expect(doc.get_CurrentVersion()).andStubReturn(doc);
    expect(doc.get_ReleasedVersion()).andStubReturn(
        isReleasedVersion ? doc : null);
    expect(doc.get_VersionStatus()).andStubReturn(
        isReleasedVersion ? VersionStatus.RELEASED : VersionStatus.SUPERSEDED);
    expect(doc.get_Owner()).andStubReturn(null);
    expect(doc.accessContentStream(eq(0))).andStubReturn(
        new ByteArrayInputStream("sample content".getBytes(UTF_8)));
    expect(doc.get_ContentSize()).andStubReturn(contentSize);
    expect(doc.get_MimeType()).andStubReturn(mimeType);
    expect(doc.get_Permissions()).andStubReturn(perms);
    expect(doc.get_ActiveMarkings()).andStubReturn(activeMarkings);
    expect(doc.getProperties()).andStubReturn(properties);
    replay(vs, properties, doc);
    objectStore.addObject(doc);
    return doc;
  }

  /**
   * Returns an ActiveMarking, backed by a Marking with the specified Id
   * and Permissions.
   */
  public static ActiveMarking mockActiveMarking(String name,
      String guid, AccessPermissionList perms) {
    Marking marking = createMock(Marking.class);
    expect(marking.get_Id()).andStubReturn(newId(guid));
    expect(marking.get_Permissions()).andStubReturn(perms);
    ActiveMarking activeMarking = createMock(ActiveMarking.class);
    expect(activeMarking.get_Marking()).andStubReturn(marking);
    expect(activeMarking.get_PropertyDisplayName()).andStubReturn(name);
    replay(marking, activeMarking);
    return activeMarking;
  }

  public static DeletionEvent mockDeletionEvent(MockObjectStore objectStore,
      String vsId, String eventId, String timeStr, boolean isReleasedVersion) {
    VersionSeries vs = createMock(VersionSeries.class);
    expect(vs.get_Id()).andStubReturn(newId(vsId));
    ObjectStore os = createMock(ObjectStore.class);
    expect(os.fetchObject(ClassNames.VERSION_SERIES, newId(vsId), null));
    if (isReleasedVersion) {
      expectLastCall().andStubThrow(new EngineRuntimeException());
    } else {
      expectLastCall().andStubReturn(vs);
    }
    DeletionEvent event = createMock(DeletionEvent.class);
    expect(event.get_Id()).andStubReturn(newId(eventId));
    expect(event.get_VersionSeriesId()).andStubReturn(newId(vsId));
    expect(event.get_DateCreated()).andStubReturn(parseTime(timeStr));
    expect(event.getObjectStore()).andStubReturn(os);
    expect(event.get_SourceObjectId()).andStubReturn(newId(vsId));
    replay(vs, os, event);
    return event;
  }
}
