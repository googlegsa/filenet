// Copyright 2007-2010 Google Inc.  All Rights Reserved.
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
import com.filenet.api.core.Document;
import com.filenet.api.core.Folder;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.util.Id;

import java.io.InputStream;
import java.util.Date;

/**
 * Core document class, which directly interacts with the core FileNet APIs
 * related to Documents.
 */
class FnDocument implements IDocument {
  private final Document doc;

  public FnDocument(Document doc) {
    this.doc = doc;
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
    return doc.get_Owner();
  }

  @Override
  public InputStream accessContentStream(int index) {
    return doc.accessContentStream(index);
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
    return doc.get_VersionSeries();
  }

  @Override
  public ActiveMarkingList get_ActiveMarkings() {
    return doc.get_ActiveMarkings();
  }

  @Override
  public Folder get_SecurityFolder() {
    return doc.get_SecurityFolder();
  }

  @Override
  public IDocumentProperties getDocumentProperties() {
    return new FnDocumentProperties(doc);
  }
}
