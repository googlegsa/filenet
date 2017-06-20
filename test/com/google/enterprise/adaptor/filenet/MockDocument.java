// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.collection.ActiveMarkingList;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.core.Document;
import com.filenet.api.core.Folder;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.util.Id;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MockDocument implements IDocument {
  private final Document doc;
  private final Map<String, Object> props;

  public MockDocument(Document object) {
    this.doc = object;
    this.props = new HashMap<String, Object>();
    props.put(PropertyNames.ID, doc.get_Id());
    props.put(PropertyNames.DATE_LAST_MODIFIED, doc.get_DateLastModified());
    props.put(PropertyNames.MIME_TYPE, doc.get_MimeType());
    Double contentSize = doc.get_ContentSize();
    props.put(PropertyNames.CONTENT_SIZE,
        (contentSize == null) ? null : contentSize.toString());
  }

  @Override
  public Id get_Id() {
    return doc.get_Id();
  }

  @Override
  public Date get_DateLastModified() {
    return doc.get_DateLastModified();
  }

  @Override
  public AccessPermissionList get_Permissions() {
    return doc.get_Permissions();
  }

  @Override
  public String get_Owner() {
    return null;
  }

  @Override
  public InputStream accessContentStream(int index) {
    return new ByteArrayInputStream(
        "sample content".getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Double get_ContentSize() {
    return doc.get_ContentSize();
  }

  @Override
  public String get_MimeType() {
    return doc.get_MimeType();
  }

  @Override
  public VersionSeries getVersionSeries() {
    // TODO(jlacey): v3 used the same Id for the version series, but
    // we need to change that for better testing.
    return Proxies.newProxyInstance(VersionSeries.class,
        new MockVersionSeries(doc.get_Id()));
  }

  private static class MockVersionSeries {
    private final Id guid;

    private MockVersionSeries(Id guid) {
      this.guid = guid;
    }

    public Id get_Id() {
      return guid;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static class ActiveMarkingListMock
      extends ArrayList implements ActiveMarkingList { }

  @Override
  public ActiveMarkingList get_ActiveMarkings() {
    return new ActiveMarkingListMock();
  }

  @Override
  public Folder get_SecurityFolder() {
    return null;
  }

  @Override
  public IDocumentProperties getDocumentProperties() {
    return new MockDocumentProperties();
  }

  private class MockDocumentProperties implements IDocumentProperties {
    @Override
    public Set<String> getPropertyNames() {
      return props.keySet();
    }

    @Override
    public void getProperty(String name, List<String> list) {
      Object obj = props.get(name);
      if (obj == null) {
        return;
      } else if (obj instanceof Date) {
        Date val = (Date) props.get(name);
        Calendar cal = Calendar.getInstance();
        cal.setTime(val);
        list.add(/*TODO: ISO 8601*/ val.toString());
      } else {
        String val = props.get(name).toString();
        list.add(val);
      }
    }
  }
}
