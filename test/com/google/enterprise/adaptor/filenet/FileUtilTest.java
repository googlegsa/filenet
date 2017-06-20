// Copyright 2013 Google Inc.
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tests the FileUtil utility class. */
public class FileUtilTest {
  @Test
  public void testConvertDn_email() {
    assertEquals("jsmith@example.com",
        FileUtil.convertDn("jsmith@example.com"));
  }

  @Test
  public void testConvertDn_netbios() {
    assertEquals("example.com\\jsmith",
        FileUtil.convertDn("example.com\\jsmith"));
  }

  @Test
  public void testConvertDn_slash() {
    assertEquals("example.com/jsmith",
        FileUtil.convertDn("example.com/jsmith"));
  }

  @Test
  public void testConvertDn_dn() {
    assertEquals("Jane Smith@example.com",
        FileUtil.convertDn("cn=Jane Smith,ou=users,dc=example,dc=com"));
  }
}
