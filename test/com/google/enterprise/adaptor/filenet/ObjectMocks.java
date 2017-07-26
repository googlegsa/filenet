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
import static org.easymock.EasyMock.anyObject;
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
import com.filenet.api.collection.BooleanList;
import com.filenet.api.collection.DateTimeList;
import com.filenet.api.collection.Float64List;
import com.filenet.api.collection.IdList;
import com.filenet.api.collection.Integer32List;
import com.filenet.api.collection.StringList;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.constants.VersionStatus;
import com.filenet.api.core.Document;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.events.DeletionEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.property.PropertyBoolean;
import com.filenet.api.property.PropertyBooleanList;
import com.filenet.api.property.PropertyDateTime;
import com.filenet.api.property.PropertyDateTimeList;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.property.PropertyFloat64;
import com.filenet.api.property.PropertyFloat64List;
import com.filenet.api.property.PropertyId;
import com.filenet.api.property.PropertyIdList;
import com.filenet.api.property.PropertyInteger32;
import com.filenet.api.property.PropertyInteger32List;
import com.filenet.api.property.PropertyString;
import com.filenet.api.property.PropertyStringList;
import com.filenet.api.security.ActiveMarking;
import com.filenet.api.security.Marking;
import com.filenet.api.util.Id;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
  public static void mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, VersionStatus versionStatus,
      Double contentSize) {
    mockDocument(objectStore, guid, timeStr, versionStatus,
        contentSize, null, new AccessPermissionListMock());
  }

  /** Gets a Document with ContentSize and MimeType properties. */
  public static void mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, VersionStatus versionStatus,
      Double contentSize, String mimeType) {
    mockDocument(objectStore, guid, timeStr, versionStatus,
        contentSize, mimeType, new AccessPermissionListMock());
  }

  /**
   * Gets a Document with ContentSize, MimeType, and Permissions properties.
   */
  public static void mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, VersionStatus versionStatus,
      Double contentSize, String mimeType, AccessPermissionList perms) {
    mockDocument(objectStore, guid, timeStr, versionStatus,
         contentSize, mimeType, perms, new ActiveMarkingListMock());
  }

  /**
   * Gets a Document with ContentSize, MimeType, ActiveMarkings, and
   * Permissions properties.
   */
  public static void mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, VersionStatus versionStatus,
      Double contentSize, String mimeType, AccessPermissionList perms,
      ActiveMarkingList activeMarkings) {
    mockDocument(objectStore, guid, timeStr, versionStatus, contentSize,
        mimeType, Collections.<Property>emptyList(), perms, activeMarkings);
  }

  /**
   * Gets a Document with ContentSize, MimeType, ActiveMarkings,
   * Permissions, and Additional Properties.
   */
  public static void mockDocument(MockObjectStore objectStore,
      String guid, String timeStr, VersionStatus versionStatus,
      Double contentSize, String mimeType, List<Property> moreProperties,
      AccessPermissionList perms, ActiveMarkingList activeMarkings) {
    VersionSeries vs = createMock(VersionSeries.class);
    expect(vs.get_Id()).andStubReturn(newId(guid));

    Property[] props = new Property[4 + moreProperties.size()];
    int i = 0;
    props[i++] = mockProperty(PropertyId.class, PropertyNames.ID, newId(guid));
    props[i++] = mockProperty(PropertyDateTime.class,
        PropertyNames.DATE_LAST_MODIFIED, parseTime(timeStr));
    props[i++] = mockProperty(PropertyString.class, PropertyNames.MIME_TYPE,
        mimeType);
    props[i++] = mockProperty(PropertyFloat64.class, PropertyNames.CONTENT_SIZE,
        contentSize);
    for (Property property : moreProperties) {
      props[i++] = property;
    }
    Properties properties = createMock(Properties.class);
    expect(properties.toArray()).andStubReturn(props);

    Document doc = createMock(Document.class);
    doc.refresh(anyObject(PropertyFilter.class));
    expectLastCall().asStub();
    expect(doc.get_Id()).andStubReturn(newId(guid));
    expect(doc.get_VersionSeries()).andStubReturn(vs);
    expect(doc.get_DateLastModified()).andStubReturn(parseTime(timeStr));
    expect(doc.get_CurrentVersion()).andStubReturn(doc);
    expect(doc.get_ReleasedVersion()).andStubReturn(
        (versionStatus == VersionStatus.RELEASED) ? doc : null);
    expect(doc.get_VersionStatus()).andStubReturn(versionStatus);
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
  }

  public static void mockDocumentNotFound(MockObjectStore objectStore,
      String guid) {
    Document doc = createMock(Document.class);
    doc.refresh(anyObject(PropertyFilter.class));
    expectLastCall().andThrow(
        new EngineRuntimeException(ExceptionCode.E_OBJECT_NOT_FOUND));
    expect(doc.get_Id()).andStubReturn(newId(guid));
    replay(doc);
    objectStore.addObject(doc);
  }

  /**
   * Returns a Property with the specified name and value, of a type derived
   * from value.
   */
  public static Property mockProperty(String name, Object value) {
    if (value instanceof Boolean) {
      return mockProperty(PropertyBoolean.class, name, value);
    } else if (value instanceof BooleanList) {
      return mockProperty(PropertyBooleanList.class, name, value);
    } else if (value instanceof Date) {
      return mockProperty(PropertyDateTime.class, name, value);
    } else if (value instanceof DateTimeList) {
      return mockProperty(PropertyDateTimeList.class, name, value);
    } else if (value instanceof Double) {
      return mockProperty(PropertyFloat64.class, name, value);
    } else if (value instanceof Float64List) {
      return mockProperty(PropertyFloat64List.class, name, value);
    } else if (value instanceof Id) {
      return mockProperty(PropertyId.class, name, value);
    } else if (value instanceof IdList) {
      return mockProperty(PropertyIdList.class, name, value);
    } else if (value instanceof Integer) {
      return mockProperty(PropertyInteger32.class, name, value);
    } else if (value instanceof Integer32List) {
      return mockProperty(PropertyInteger32List.class, name, value);
    } else if (value instanceof String) {
      return mockProperty(PropertyString.class, name, value);
    } else if (value instanceof StringList) {
      return mockProperty(PropertyStringList.class, name, value);
    } else if (value == null) {
      throw new IllegalArgumentException("Property value may not be null.");
    } else {
      throw new IllegalArgumentException("Unsupported Property type: "
          + value.getClass().getName());
    }
  }

  /**
   * Returns a Property with the specified type, name, and value.
   */
  public static Property mockProperty(Class<? extends Property>  clazz,
        String name, Object value) {
    Property property = createMock(clazz);
    expect(property.getPropertyName()).andStubReturn(name);
    if (PropertyBoolean.class.equals(clazz)) {
      expect(property.getBooleanValue()).andStubReturn((Boolean) value);
    } else if (PropertyBooleanList.class.equals(clazz)) {
      expect(property.getBooleanListValue()).andStubReturn((BooleanList) value);
    } else if (PropertyDateTime.class.equals(clazz)) {
      expect(property.getDateTimeValue()).andStubReturn((Date) value);
    } else if (PropertyDateTimeList.class.equals(clazz)) {
      expect(property.getDateTimeListValue())
          .andStubReturn((DateTimeList) value);
    } else if (PropertyFloat64.class.equals(clazz)) {
      expect(property.getFloat64Value()).andStubReturn((Double) value);
    } else if (PropertyFloat64List.class.equals(clazz)) {
      expect(property.getFloat64ListValue()).andStubReturn((Float64List) value);
    } else if (PropertyId.class.equals(clazz)) {
      expect(property.getIdValue()).andStubReturn((Id) value);
    } else if (PropertyIdList.class.equals(clazz)) {
      expect(property.getIdListValue()).andStubReturn((IdList) value);
    } else if (PropertyInteger32.class.equals(clazz)) {
      expect(property.getInteger32Value()).andStubReturn((Integer) value);
    } else if (PropertyInteger32List.class.equals(clazz)) {
      expect(property.getInteger32ListValue())
          .andStubReturn((Integer32List) value);
    } else if (PropertyString.class.equals(clazz)) {
      expect(property.getStringValue()).andStubReturn((String) value);
    } else if (PropertyStringList.class.equals(clazz)) {
      expect(property.getStringListValue()).andStubReturn((StringList) value);
    } // Intentionally allow unsupported PropertyTypes to be mocked.
    replay(property);
    return property;
  }

  /**
   * Returns an ActiveMarking, backed by a Marking with the specified Id
   * and Permissions.
   */
  public static ActiveMarking mockActiveMarking(String name,
      String guid, AccessPermissionList perms, Integer constraintMask) {
    Marking marking = createMock(Marking.class);
    expect(marking.get_Id()).andStubReturn(newId(guid));
    expect(marking.get_Permissions()).andStubReturn(perms);
    expect(marking.get_ConstraintMask()).andStubReturn(constraintMask);
    ActiveMarking activeMarking = createMock(ActiveMarking.class);
    expect(activeMarking.get_Marking()).andStubReturn(marking);
    expect(activeMarking.get_PropertyDisplayName()).andStubReturn(name);
    replay(marking, activeMarking);
    return activeMarking;
  }

  public static DeletionEvent mockDeletionEvent(MockObjectStore objectStore,
      String vsId, String eventId, String timeStr,
      VersionStatus versionStatus) {
    VersionSeries vs = createMock(VersionSeries.class);
    expect(vs.get_Id()).andStubReturn(newId(vsId));
    ObjectStore os = createMock(ObjectStore.class);
    expect(os.fetchObject(ClassNames.VERSION_SERIES, newId(vsId), null));
    if (versionStatus == VersionStatus.RELEASED) {
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
