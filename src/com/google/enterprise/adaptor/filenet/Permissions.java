// Copyright 2007-2010 Google Inc. All Rights Reserved.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.AccessType;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.SecurityPrincipalType;
import com.filenet.api.security.AccessPermission;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Wrapper class over the FileNet API class Permissions.
 */
class Permissions {
  public static final String AUTHENTICATED_USERS = "#AUTHENTICATED-USERS";
  private static final String CREATOR_OWNER = "#CREATOR-OWNER";

  public static final int VIEW_ACCESS_RIGHTS =
      AccessRight.READ_AS_INT | AccessRight.VIEW_CONTENT_AS_INT;
  private static final int USE_MARKING = AccessRight.USE_MARKING_AS_INT;

  private final SetMultimap<PermissionSource, String> allowUsers =
      HashMultimap.create();
  private final SetMultimap<PermissionSource, String> allowGroups =
      HashMultimap.create();
  private final SetMultimap<PermissionSource, String> denyUsers =
      HashMultimap.create();
  private final SetMultimap<PermissionSource, String> denyGroups =
      HashMultimap.create();

  /** Collect Regular (Non-Marking) Permissions. */
  public Permissions(AccessPermissionList perms, String owner) {
    // TODO(bmj): Implement owners in ACLs.
    Iterator<?> iter = perms.iterator();
    while (iter.hasNext()) {
      AccessPermission perm = (AccessPermission) iter.next();
      int mask = perm.get_AccessMask();
      if ((mask & VIEW_ACCESS_RIGHTS) != VIEW_ACCESS_RIGHTS) {
        continue;
      }
      if (perm.get_AccessType() == AccessType.ALLOW) {
        if (perm.get_GranteeType() == SecurityPrincipalType.USER) {
          allowUsers.put(perm.get_PermissionSource(), perm.get_GranteeName());
        } else {
          allowGroups.put(perm.get_PermissionSource(), perm.get_GranteeName());
        }
      } else {
        if (perm.get_GranteeType() == SecurityPrincipalType.USER) {
          denyUsers.put(perm.get_PermissionSource(), perm.get_GranteeName());
        } else {
          denyGroups.put(perm.get_PermissionSource(), perm.get_GranteeName());
        }
      }
    }
  }

  /** Collect Marking Permissions. */
  @SuppressWarnings("deprecation")  // For PermissionSource.MARKING
  public Permissions(AccessPermissionList perms, int constraintMask) {
    Iterator<?> iter = perms.iterator();
    while (iter.hasNext()) {
      AccessPermission perm = (AccessPermission) iter.next();
      int mask = perm.get_AccessMask();
      if ((mask & USE_MARKING) != USE_MARKING) {
        continue;
      }
      if (perm.get_AccessType() == AccessType.ALLOW) {
        if (perm.get_GranteeType() == SecurityPrincipalType.USER) {
          allowUsers.put(PermissionSource.MARKING, perm.get_GranteeName());
        } else {
          allowGroups.put(PermissionSource.MARKING, perm.get_GranteeName());
        }
      } else {
        if (perm.get_GranteeType() == SecurityPrincipalType.USER) {
          denyUsers.put(PermissionSource.MARKING, perm.get_GranteeName());
        } else {
          denyGroups.put(PermissionSource.MARKING, perm.get_GranteeName());
        }
      }
    }
    boolean authorizeByConstraints =
        (VIEW_ACCESS_RIGHTS & ~constraintMask) == VIEW_ACCESS_RIGHTS;
    if (authorizeByConstraints) {
      // TODO(bmj): AUTHENTICATED_USERS should really be feed as a local
      // group, not a global group.
      allowGroups.put(PermissionSource.MARKING, AUTHENTICATED_USERS);
    } else {
      // If we added a denyGroups of AUTHENTICATED_USERS, the ACL would
      // end up denying everybody, since the ACL chain would not reflect
      // how Use rights trump the constraint mask. However, AND_BOTH_PERMIT
      // allows this to work itself out, since anyone not explicitly
      // granted ALLOW rights, will be denied anyway.
    }
  }

  public Set<String> getAllowUsers() {
    return new HashSet<String>(allowUsers.values());
  }

  public Set<String> getAllowUsers(PermissionSource permSrc) {
    return allowUsers.get(permSrc);
  }

  public Set<String> getAllowGroups() {
    return new HashSet<String>(allowGroups.values());
  }

  public Set<String> getAllowGroups(PermissionSource permSrc) {
    return allowGroups.get(permSrc);
  }

  public Set<String> getDenyUsers() {
    return new HashSet<String>(denyUsers.values());
  }

  public Set<String> getDenyUsers(PermissionSource permSrc) {
    return denyUsers.get(permSrc);
  }

  public Set<String> getDenyGroups() {
    return new HashSet<String>(denyGroups.values());
  }

  public Set<String> getDenyGroups(PermissionSource permSrc) {
    return denyGroups.get(permSrc);
  }
}
