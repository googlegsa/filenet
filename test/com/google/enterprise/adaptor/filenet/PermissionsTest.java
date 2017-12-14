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

import static com.google.enterprise.adaptor.filenet.Permissions.AUTHENTICATED_USERS;
import static com.google.enterprise.adaptor.filenet.Permissions.VIEW_ACCESS_RIGHTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.AccessPermissionListMock;

import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.AccessType;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.SecurityPrincipalType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

/** Tests the Permissions utility class. */
public class PermissionsTest {

  private AccessPermissionListMock perms;

  @Before
  public void setUp() {
    perms = new AccessPermissionListMock();
  }

  @After
  public void tearDown() {
    perms.clear();
  }

  @Test
  public void testEmptyPermissionList() {
    Permissions emptyPerms =
        new Permissions(new AccessPermissionListMock(), null);
    assertEquals(0, emptyPerms.getAllowUsers().size());
    assertEquals(0, emptyPerms.getAllowGroups().size());
    assertEquals(0, emptyPerms.getDenyUsers().size());
    assertEquals(0, emptyPerms.getDenyGroups().size());
  }

  private void populateAces(int maxPerGroup, boolean includeAllowUsers,
      boolean includeDenyUsers, boolean includeAllowGroups,
      boolean includeDenyGroups, PermissionSource permSrc) {
    if (includeAllowUsers) {
      addAces(maxPerGroup, AccessType.ALLOW, SecurityPrincipalType.USER,
          VIEW_ACCESS_RIGHTS, 0, permSrc);
    }
    if (includeDenyUsers) {
      addAces(maxPerGroup, AccessType.DENY, SecurityPrincipalType.USER,
          VIEW_ACCESS_RIGHTS, 0, permSrc);
    }
    if (includeAllowGroups) {
      addAces(maxPerGroup, AccessType.ALLOW, SecurityPrincipalType.GROUP,
          VIEW_ACCESS_RIGHTS, 0, permSrc);
    }
    if (includeDenyGroups) {
      addAces(maxPerGroup, AccessType.DENY, SecurityPrincipalType.GROUP,
          VIEW_ACCESS_RIGHTS, 0, permSrc);
    }
    Collections.shuffle(perms);
  }

  @SuppressWarnings({"unchecked"})
  private void addAce(PermissionSource permSrc,
      SecurityPrincipalType principalType, AccessType accessType,
      int accessMask, int inheritableDepth, String granteeName) {
    perms.add(TestObjectFactory.newPermission(granteeName, principalType,
        accessType, accessMask, inheritableDepth, permSrc));
  }

  private void addAces(int numOfAces, AccessType accessType,
      SecurityPrincipalType secType, int accessMask, int inheritDepth,
      PermissionSource... permSrcs) {
    for (PermissionSource permSrc : permSrcs) {
      for (int i = 0; i < numOfAces; i++) {
        String grantee = permSrc.toString() + " "
            + accessType.toString().toLowerCase() + " "
            + secType.toString().toLowerCase() + " " + i;
        addAce(permSrc, secType, accessType, accessMask, inheritDepth, grantee);
      }
    }
  }

  @Test
  public void testEmptyAllowUsers() {
    populateAces(10, false, true, true, true, PermissionSource.SOURCE_DIRECT);
    Permissions testPerms = new Permissions(perms, null);
    assertEquals(0, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyDenyUsers() {
    populateAces(10, true, false, true, true, PermissionSource.SOURCE_DIRECT);
    Permissions testPerms = new Permissions(perms, null);
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(0, testPerms.getDenyUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyAllowGroups() {
    populateAces(10, true, true, false, true, PermissionSource.SOURCE_DIRECT);
    Permissions testPerms = new Permissions(perms, null);
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(0, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyDenyGroups() {
    populateAces(10, true, true, true, false, PermissionSource.SOURCE_DIRECT);
    Permissions testPerms = new Permissions(perms, null);
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(0, testPerms.getDenyGroups().size());
  }

  private Permissions getObjectUnderTest(int maxAllowUsers,
      int maxAllowGroups, int maxDenyUsers, int maxDenyGroups,
      PermissionSource... permSrcs) {
    addAces(maxAllowUsers, AccessType.ALLOW, SecurityPrincipalType.USER,
        VIEW_ACCESS_RIGHTS, 0, permSrcs);
    addAces(maxAllowGroups, AccessType.ALLOW, SecurityPrincipalType.GROUP,
        VIEW_ACCESS_RIGHTS, 0, permSrcs);
    addAces(maxDenyUsers, AccessType.DENY, SecurityPrincipalType.USER,
        VIEW_ACCESS_RIGHTS, 0, permSrcs);
    addAces(maxDenyGroups, AccessType.DENY, SecurityPrincipalType.GROUP,
        VIEW_ACCESS_RIGHTS, 0, permSrcs);
    Collections.shuffle(perms);

    return new Permissions(perms, null);
  }

  private void assertSetContains(Set<String> theSet, String prefix, int size) {
    for (int i = 0; i < size; i++) {
      assertTrue(theSet.contains(prefix + i));
    }
  }

  @Test
  public void testGetAllowUsers() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualAllowUsers = testPerms.getAllowUsers();
    assertEquals(8, actualAllowUsers.size());
    assertSetContains(actualAllowUsers,
        PermissionSource.SOURCE_DIRECT.toString() + " allow user ", 8);
  }

  @Test
  public void testGetAllowUsersBySource() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT, PermissionSource.SOURCE_DEFAULT);
    Set<String> inheritAllowUsers =
        testPerms.getAllowUsers(PermissionSource.SOURCE_PARENT);
    assertEquals(0, inheritAllowUsers.size());

    Set<String> actualDirectAllowUsers =
        testPerms.getAllowUsers(PermissionSource.SOURCE_DIRECT);
    assertEquals(8, actualDirectAllowUsers.size());
    assertSetContains(actualDirectAllowUsers,
        PermissionSource.SOURCE_DIRECT.toString() + " allow user ", 8);
  }

  @Test
  public void testGetDenyUsers() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualDenyUsers = testPerms.getDenyUsers();
    assertEquals(6, actualDenyUsers.size());
    assertSetContains(actualDenyUsers,
        PermissionSource.SOURCE_DIRECT.toString() + " deny user ", 6);
  }

  @Test
  public void testGetDenyUsersBySource() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_PARENT, PermissionSource.SOURCE_DEFAULT);
    Set<String> directDenyUsers =
        testPerms.getDenyUsers(PermissionSource.SOURCE_DIRECT);
    assertEquals(0, directDenyUsers.size());

    Set<String> actualInheritDenyUsers =
        testPerms.getDenyUsers(PermissionSource.SOURCE_PARENT);
    assertEquals(6, actualInheritDenyUsers.size());
    assertSetContains(actualInheritDenyUsers,
        PermissionSource.SOURCE_PARENT.toString() + " deny user ", 6);
  }

  @Test
  public void testGetAllowGroups() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualAllowGroups = testPerms.getAllowGroups();
    assertEquals(7, actualAllowGroups.size());
    assertSetContains(actualAllowGroups,
        PermissionSource.SOURCE_DIRECT.toString() + " allow group ", 7);
  }

  @Test
  public void testGetAllowGroupsBySource() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT, PermissionSource.SOURCE_DEFAULT);
    Set<String> inheritAllowGroups =
        testPerms.getAllowGroups(PermissionSource.SOURCE_PARENT);
    assertEquals(0, inheritAllowGroups.size());

    Set<String> actualDirectAllowGroups =
        testPerms.getAllowGroups(PermissionSource.SOURCE_DIRECT);
    assertEquals(7, actualDirectAllowGroups.size());
    assertSetContains(actualDirectAllowGroups,
            PermissionSource.SOURCE_DIRECT.toString() + " allow group ", 7);
  }

  @Test
  public void testGetDenyGroups() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualDenyGroups = testPerms.getDenyGroups();
    assertEquals(5, actualDenyGroups.size());
    assertSetContains(actualDenyGroups,
        PermissionSource.SOURCE_DIRECT.toString() + " deny group ", 5);
  }

  @Test
  public void testGetDenyGroupsBySource() {
    Permissions testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_PARENT, PermissionSource.SOURCE_DEFAULT);
    Set<String> directDenyGroups =
        testPerms.getDenyGroups(PermissionSource.SOURCE_DIRECT);
    assertEquals(0, directDenyGroups.size());

    Set<String> actualInheritDenyGroups =
        testPerms.getDenyGroups(PermissionSource.SOURCE_PARENT);
    assertEquals(5, actualInheritDenyGroups.size());
    assertSetContains(actualInheritDenyGroups,
        PermissionSource.SOURCE_PARENT.toString() + " deny group ", 5);
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_emptyAcl_allowConstraintMask() throws Exception {
    Permissions testPerms =
        new Permissions(new AccessPermissionListMock(), ~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        testPerms.getAllowGroups(PermissionSource.MARKING));
    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @Test
  public void testMarkingAcl_emptyAcl_denyConstraintMask() throws Exception {
    Permissions testPerms =
        new Permissions(new AccessPermissionListMock(), VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @Test
  public void testMarkingAcl_emptyAcl_partialConstraintMask() throws Exception {
    Permissions testPerms = new Permissions(new AccessPermissionListMock(),
        ~AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_allowConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING),
        ~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        testPerms.getAllowGroups(PermissionSource.MARKING));
    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_denyConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING),
        VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_partialConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING),
        ~AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of(), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of(), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of(), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of(), testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_allowConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions(), ~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        testPerms.getAllowUsers());
    assertEquals(
        ImmutableSet.of("MARKING_allow_group_0", AUTHENTICATED_USERS),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_denyConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions(), ~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        testPerms.getAllowUsers());
    assertEquals(
        ImmutableSet.of("MARKING_allow_group_0", AUTHENTICATED_USERS),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_partialConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions(), AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of("MARKING_allow_group_0"),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_someUseMarking_allowConstraintMask()
      throws Exception {
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.ALLOW, AccessRight.NONE_AS_INT, 0, "allowUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, AccessRight.NONE_AS_INT, 0, "allowGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowGroup2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, VIEW_ACCESS_RIGHTS, 0, "denyUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.NONE_AS_INT, 0, "denyGroup2");

    Permissions testPerms = new Permissions(perms, ~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("allowUser1"), testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2", AUTHENTICATED_USERS),
        testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1"), testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_someUseMarking_denyConstraintMask()
      throws Exception {
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, AccessRight.NONE_AS_INT, 0, "allowGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowGroup2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, VIEW_ACCESS_RIGHTS, 0, "denyUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyGroup2");

    Permissions testPerms = new Permissions(perms, VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("allowUser1", "allowUser2"),
        testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2"), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1", "denyGroup2"),
        testPerms.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_someUseMarking_partialConstraintMask()
      throws Exception {
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
           AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, VIEW_ACCESS_RIGHTS, 0, "allowGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.ALLOW, AccessRight.USE_MARKING_AS_INT, 0, "allowGroup2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, AccessRight.NONE_AS_INT, 0, "denyUser1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.USER,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyUser2");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyGroup1");
    addAce(PermissionSource.MARKING, SecurityPrincipalType.GROUP,
        AccessType.DENY, AccessRight.USE_MARKING_AS_INT, 0, "denyGroup2");

    Permissions testPerms = new Permissions(perms, AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of("allowUser1", "allowUser2"),
        testPerms.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2"), testPerms.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), testPerms.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1", "denyGroup2"),
        testPerms.getDenyGroups());
  }
}
