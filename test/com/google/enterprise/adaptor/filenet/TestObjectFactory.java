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

import com.google.common.collect.ImmutableMap;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.SensitiveValueDecoder;
import com.google.enterprise.adaptor.filenet.EngineCollectionMocks.AccessPermissionListMock;

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.AccessType;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.SecurityPrincipalType;
import com.filenet.api.security.AccessPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TestObjectFactory {
  private static final SensitiveValueDecoder SENSITIVE_VALUE_DECODER =
      new SensitiveValueDecoder() {
        @Override
        public String decodeValue(String notEncodedDuringTesting) {
          return notEncodedDuringTesting;
        }
      };

  public static ConfigOptions newConfigOptions() {
    return newConfigOptions(ImmutableMap.<String, String>of());
  }

  public static ConfigOptions newConfigOptions(Map<String, String> extra) {
    FileNetAdaptor adaptor = new FileNetAdaptor();
    Config config = new Config();
    adaptor.initConfig(config);
    config.overrideKey("filenet.username", "whatever");
    config.overrideKey("filenet.password", "opensesame");
    config.overrideKey("filenet.objectStore", "ObjStore");
    config.overrideKey("filenet.displayUrl", "http://localhost/getContent");
    config.overrideKey("filenet.objectFactory", FileNetProxies.class.getName());
    config.overrideKey("filenet.contentEngineUrl",
        "http://localhost/wsi/FNCEWS40MTOM");
    config.overrideKey("adaptor.namespace", "ns");
    for (Map.Entry<String, String> entry : extra.entrySet()) {
      config.overrideKey(entry.getKey(), entry.getValue());
    }
    return new ConfigOptions(config, SENSITIVE_VALUE_DECODER);
  }

  public static AccessPermissionList getPermissions(
      PermissionSource... sources) {
    List<AccessPermission> aces = new ArrayList<>();
    for (PermissionSource source : sources) {
      aces.addAll(generatePermissions(
        1, 1, 1, 1, (AccessRight.READ_AS_INT | AccessRight.VIEW_CONTENT_AS_INT),
        0, source));
    }
    return newPermissionList(aces);
  }

  // TODO(tdnguyen): Combine this method with similar methods in
  // PermissionsTest.
  public static AccessPermission newPermission(String granteeName,
      SecurityPrincipalType principalType, AccessType accessType,
      int accessMask, int inheritableDepth, PermissionSource permSrc) {
    AccessPermissionMock perm = new AccessPermissionMock(permSrc);
    perm.set_GranteeType(principalType);
    perm.set_AccessType(accessType);
    perm.set_AccessMask(accessMask);
    perm.set_InheritableDepth(inheritableDepth);
    perm.set_GranteeName(granteeName);
    return perm;
  }

  @SafeVarargs
  @SuppressWarnings({"unchecked"})
  public static AccessPermissionList newPermissionList(
      List<AccessPermission>... aceLists) {
    AccessPermissionListMock perms = new AccessPermissionListMock();
    for (List<AccessPermission> aceList : aceLists) {
      perms.addAll(aceList);
    }
    return perms;
  }

  public static List<AccessPermission> generatePermissions(int allowUserCount,
          int denyUserCount, int allowGroupCount, int denyGroupCount,
          int accessMask, int inheritableDepth, PermissionSource permSrc) {
    String prefix = permSrc.toString();
    List<AccessPermission> aces = new ArrayList<AccessPermission>();
    aces.addAll(generatePermissions(prefix + "_allow_user", allowUserCount,
        SecurityPrincipalType.USER, AccessType.ALLOW, accessMask,
        inheritableDepth, permSrc));
    aces.addAll(generatePermissions(prefix + "_deny_user", denyUserCount,
        SecurityPrincipalType.USER, AccessType.DENY, accessMask,
        inheritableDepth, permSrc));
    aces.addAll(generatePermissions(prefix + "_allow_group", allowGroupCount,
        SecurityPrincipalType.GROUP, AccessType.ALLOW, accessMask,
        inheritableDepth, permSrc));
    aces.addAll(generatePermissions(prefix + "_deny_group", denyGroupCount,
        SecurityPrincipalType.GROUP, AccessType.DENY, accessMask,
        inheritableDepth, permSrc));
    return aces;
  }

  public static List<AccessPermission> generatePermissions(
      String granteePrefix, int count, SecurityPrincipalType principalType,
      AccessType accessType, int accessMask, int inheritableDepth,
      PermissionSource permSrc) {
    List<AccessPermission> aces = new ArrayList<AccessPermission>();
    for (int i = 0; i < count; i++) {
      aces.add(TestObjectFactory.newPermission(granteePrefix + "_" + i,
          principalType, accessType, accessMask, inheritableDepth, permSrc));
    }
    return aces;
  }
}
