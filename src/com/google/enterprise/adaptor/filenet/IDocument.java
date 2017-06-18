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
import com.filenet.api.core.Folder;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;

interface IDocument extends IBaseObject {
  Date get_DateLastModified();

  AccessPermissionList get_Permissions();

  String get_Owner();

  InputStream getContent();

  Double get_ContentSize();

  String get_MimeType();

  IVersionSeries getVersionSeries();

  ActiveMarkingList get_ActiveMarkings();

  Folder get_SecurityFolder();

  Set<String> getPropertyNames();

  void getProperty(String name, List<String> list);

  void getPropertyStringValue(String name, List<String> list);

  void getPropertyDateValue(String name, List<String> list);
}
