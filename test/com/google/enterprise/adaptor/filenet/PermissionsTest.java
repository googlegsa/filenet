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
import com.filenet.api.security.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

/** Tests the Permissions utility class. */
public class PermissionsTest {

  private AccessPermissionListMock perms;
  private User user;

  @Before
  public void setUp() {
    perms = new AccessPermissionListMock();
    user = SecurityPrincipalMocks.createAdministratorUser();
  }

  @After
  public void tearDown() {
    perms.clear();
  }

  @Test
  public void testEmptyPermissionList() {
    assertEquals("Access permission list is not empty", 0, perms.size());
    Permissions.Acl emptyPerms = new Permissions(perms).getAcl();
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
      SecurityPrincipalType secPrincipalType, AccessType accessType,
      int accessMask, int inheritableDepth, String ace) {
    AccessPermissionMock perm = new AccessPermissionMock(permSrc);
    perm.set_GranteeType(secPrincipalType);
    perm.set_AccessType(accessType);
    perm.set_AccessMask(accessMask);
    perm.set_InheritableDepth(inheritableDepth);
    perm.set_GranteeName(ace);
    perms.add(perm);
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
    Permissions.Acl testPerms = new Permissions(perms).getAcl();
    assertEquals(0, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyDenyUsers() {
    populateAces(10, true, false, true, true, PermissionSource.SOURCE_DIRECT);
    Permissions.Acl testPerms = new Permissions(perms).getAcl();
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(0, testPerms.getDenyUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyAllowGroups() {
    populateAces(10, true, true, false, true, PermissionSource.SOURCE_DIRECT);
    Permissions.Acl testPerms = new Permissions(perms).getAcl();
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(0, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyGroups().size());
  }

  @Test
  public void testEmptyDenyGroups() {
    populateAces(10, true, true, true, false, PermissionSource.SOURCE_DIRECT);
    Permissions.Acl testPerms = new Permissions(perms).getAcl();
    assertEquals(10, testPerms.getAllowUsers().size());
    assertEquals(10, testPerms.getAllowGroups().size());
    assertEquals(10, testPerms.getDenyUsers().size());
    assertEquals(0, testPerms.getDenyGroups().size());
  }

  private Permissions.Acl getObjectUnderTest(int maxAllowUsers,
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

    return new Permissions(perms).getAcl();
  }

  private void assertSetContains(Set<String> theSet, String prefix, int size) {
    for (int i = 0; i < size; i++) {
      assertTrue(theSet.contains(prefix + i));
    }
  }

  @Test
  public void testGetAllowUsers() {
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualAllowUsers = testPerms.getAllowUsers();
    assertEquals(8, actualAllowUsers.size());
    assertSetContains(actualAllowUsers,
        PermissionSource.SOURCE_DIRECT.toString() + " allow user ", 8);
  }

  @Test
  public void testGetAllowUsersBySource() {
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
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
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualDenyUsers = testPerms.getDenyUsers();
    assertEquals(6, actualDenyUsers.size());
    assertSetContains(actualDenyUsers,
        PermissionSource.SOURCE_DIRECT.toString() + " deny user ", 6);
  }

  @Test
  public void testGetDenyUsersBySource() {
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
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
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualAllowGroups = testPerms.getAllowGroups();
    assertEquals(7, actualAllowGroups.size());
    assertSetContains(actualAllowGroups,
        PermissionSource.SOURCE_DIRECT.toString() + " allow group ", 7);
  }

  @Test
  public void testGetAllowGroupsBySource() {
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
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
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
        PermissionSource.SOURCE_DIRECT);
    Set<String> actualDenyGroups = testPerms.getDenyGroups();
    assertEquals(5, actualDenyGroups.size());
    assertSetContains(actualDenyGroups,
        PermissionSource.SOURCE_DIRECT.toString() + " deny group ", 5);
  }

  @Test
  public void testGetDenyGroupsBySource() {
    Permissions.Acl testPerms = getObjectUnderTest(8, 7, 6, 5,
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
    Permissions testPerms = new Permissions(new AccessPermissionListMock());
    Permissions.Acl acl = testPerms.getMarkingAcl(~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        acl.getAllowGroups(PermissionSource.MARKING));
    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @Test
  public void testMarkingAcl_emptyAcl_denyConstraintMask() throws Exception {
    Permissions testPerms = new Permissions(new AccessPermissionListMock());
    Permissions.Acl acl = testPerms.getMarkingAcl(VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @Test
  public void testMarkingAcl_emptyAcl_partialConstraintMask() throws Exception {
    Permissions testPerms = new Permissions(new AccessPermissionListMock());
    Permissions.Acl acl = testPerms.getMarkingAcl(~AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_allowConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING));
    Permissions.Acl acl = testPerms.getMarkingAcl(~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        acl.getAllowGroups(PermissionSource.MARKING));
    assertEquals(ImmutableSet.of(AUTHENTICATED_USERS),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_denyConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING));
    Permissions.Acl acl = testPerms.getMarkingAcl(VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_noUseMarking_partialConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getPermissions(PermissionSource.MARKING));
    Permissions.Acl acl = testPerms.getMarkingAcl(~AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of(), acl.getAllowUsers());
    assertEquals(ImmutableSet.of(), acl.getAllowGroups());
    assertEquals(ImmutableSet.of(), acl.getDenyUsers());
    assertEquals(ImmutableSet.of(), acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_allowConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions());
    Permissions.Acl acl = testPerms.getMarkingAcl(~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        acl.getAllowUsers());
    assertEquals(
        ImmutableSet.of("MARKING_allow_group_0", AUTHENTICATED_USERS),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        acl.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_denyConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions());
    Permissions.Acl acl = testPerms.getMarkingAcl(VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        acl.getAllowUsers());
    assertEquals(ImmutableSet.of("MARKING_allow_group_0"),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        acl.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        acl.getDenyGroups());
  }

  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  @Test
  public void testMarkingAcl_allUseMarking_partialConstraintMask()
      throws Exception {
    Permissions testPerms = new Permissions(
        TestObjectFactory.getMarkingPermissions());
    Permissions.Acl acl = testPerms.getMarkingAcl(AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of("MARKING_allow_user_0"),
        acl.getAllowUsers());
    assertEquals(ImmutableSet.of("MARKING_allow_group_0"),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of("MARKING_deny_user_0"),
        acl.getDenyUsers());
    assertEquals(ImmutableSet.of("MARKING_deny_group_0"),
        acl.getDenyGroups());
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

    Permissions testPerms = new Permissions(perms);
    Permissions.Acl acl = testPerms.getMarkingAcl(~VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("allowUser1"), acl.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2", AUTHENTICATED_USERS),
        acl.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), acl.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1"), acl.getDenyGroups());
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

    Permissions testPerms = new Permissions(perms);
    Permissions.Acl acl = testPerms.getMarkingAcl(VIEW_ACCESS_RIGHTS);

    assertEquals(ImmutableSet.of("allowUser1", "allowUser2"),
        acl.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2"), acl.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), acl.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1", "denyGroup2"),
        acl.getDenyGroups());
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

    Permissions testPerms = new Permissions(perms);
    Permissions.Acl acl = testPerms.getMarkingAcl(AccessRight.READ_AS_INT);

    assertEquals(ImmutableSet.of("allowUser1", "allowUser2"),
        acl.getAllowUsers());
    assertEquals(ImmutableSet.of("allowGroup2"), acl.getAllowGroups());
    assertEquals(ImmutableSet.of("denyUser2"), acl.getDenyUsers());
    assertEquals(ImmutableSet.of("denyGroup1", "denyGroup2"),
        acl.getDenyGroups());
  }
}
