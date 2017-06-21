// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.filenet.api.core.Connection;
import com.filenet.api.core.ObjectReference;
import com.filenet.api.meta.ClassDescription;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.security.SecurityPrincipal;

class SecurityPrincipalMock implements SecurityPrincipal {
  protected SecurityPrincipalMock() {
  }

  @Override
  public void fetchProperties(String[] arg0) {
    // unimplemented
  }

  @Override
  public void fetchProperties(PropertyFilter arg0) {
    // unimplemented
  }

  @Override
  public Property fetchProperty(String arg0, PropertyFilter arg1) {
    return null;
  }

  @Override
  public Property fetchProperty(String arg0, PropertyFilter arg1,
      Integer arg2) {
    return null;
  }

  @Override
  public ObjectReference getObjectReference() {
    return null;
  }

  @Override
  public void refresh() {
    // unimplemented
  }

  @Override
  public void refresh(String[] arg0) {
    // unimplemented
  }

  @Override
  public void refresh(PropertyFilter arg0) {
    // unimplemented
  }

  @Override
  public String getClassName() {
    return getClass().getName();
  }

  @Override
  public Connection getConnection() {
    return null;
  }

  @Override
  public Properties getProperties() {
    return null;
  }

  @Override
  public String[] getSuperClasses() {
    return null;
  }

  @Override
  public ClassDescription get_ClassDescription() {
    return null;
  }
}
