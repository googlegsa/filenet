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

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableSet;

import com.filenet.api.constants.PropertyNames;
import com.filenet.api.property.PropertyFilter;

import org.junit.Test;

import java.util.List;

/** Tests the FileUtil utility class. */
public class FileUtilTest {
  /** Tests that including nothing explicitly fetches everything. */
  @Test
  public void testGetDocumentPropertyFilter_emptyEmpty() {
    assertNull(
        FileUtil.getDocumentPropertyFilter(
            ImmutableSet.<String>of(),
            ImmutableSet.<String>of()));
  }

  /** Tests that excluding properties still fetches everything. */
  @Test
  public void testGetDocumentPropertyFilter_emptyNonempty() {
    assertNull(
        FileUtil.getDocumentPropertyFilter(
            ImmutableSet.<String>of(),
            ImmutableSet.of(PropertyNames.DATE_CREATED, PropertyNames.ID)));
  }

  /** Tests that included properties are added to the filter. */
  @Test
  public void testGetDocumentPropertyFilter_nonemptyEmpty() {
    PropertyFilter filter =
        FileUtil.getDocumentPropertyFilter(
            // This should be something that we don't fetch by default.
            ImmutableSet.of(PropertyNames.DATE_CREATED),
            ImmutableSet.<String>of());
    assertNotNull(filter);
    assertEquals(filter.toString(), 1, filter.getIncludeProperties().length);
    List<String> names =
        asList(filter.getIncludeProperties()[0].getValue().split(" "));
    assertThat(names, hasItem(PropertyNames.DATE_CREATED));
    assertThat(names, hasItem(PropertyNames.ID));
  }

  /** Tests that we can exclude included properties, but not builtin ones. */
  @Test
  public void testGetDocumentPropertyFilter_nonemptyNonempty() {
    PropertyFilter filter =
        FileUtil.getDocumentPropertyFilter(
            // This should be something that we don't fetch by default.
            ImmutableSet.of(PropertyNames.DATE_CREATED),
            ImmutableSet.of(PropertyNames.DATE_CREATED, PropertyNames.ID));
    assertNotNull(filter);
    assertEquals(filter.toString(), 1, filter.getIncludeProperties().length);
    List<String> names =
        asList(filter.getIncludeProperties()[0].getValue().split(" "));
    assertThat(names, not(hasItem(PropertyNames.DATE_CREATED)));
    assertThat(names, hasItem(PropertyNames.ID));
  }

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
