// Copyright 2010 Google Inc. All Rights Reserved.
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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import com.filenet.api.constants.PropertyNames;
import com.filenet.api.property.FilterElement;
import com.filenet.api.property.PropertyFilter;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileUtil {
  private static final Logger logger =
      Logger.getLogger(FileUtil.class.getName());

  private FileUtil() {
  }

  /**
   * getShortName takes a string as parameter and parses it to get the
   * shortname. It supports User Principle Name (UPN) and Full
   * Distinguished Name (DN) format.
   *
   * @param longName Username in the form of UPN or Full DN format.
   * @return ShortName of the Username (Which may be in one of the
   *     form i.e. UPN or Full DN format.)
   */
  public static String getShortName(String longName) {
    StringTokenizer strtok = new StringTokenizer(longName, ",");
    String shortUserName = null;
    if (strtok.countTokens() > 1) {
      while (strtok != null && strtok.hasMoreTokens()) {
        String mytok1 = strtok.nextToken();
        if (mytok1 != null) {
          //filter for the shortened name
          StringTokenizer innerToken = new StringTokenizer(mytok1, "=");
          if (innerToken != null && innerToken.countTokens() == 2) {
            String key = innerToken.nextToken();
            if (key != null) {
              if (key.equalsIgnoreCase("cn") || key.equalsIgnoreCase("uid")) {
                shortUserName = innerToken.nextToken();
                break;
              }
            }
          }
        }
      }
    } else if (longName.contains("@")) {
      shortUserName = longName.substring(0, longName.indexOf("@"));
    }
    return shortUserName;
  }

  /** Creates a default property filter for document. */
  public static PropertyFilter getDocumentPropertyFilter(
      Set<String> includedMetaNames) {
    Set<String> filterSet = new HashSet<String>();
    if (includedMetaNames != null) {
      filterSet.addAll(includedMetaNames);
    }
    filterSet.add(PropertyNames.ID);
    filterSet.add(PropertyNames.CLASS_DESCRIPTION);
    filterSet.add(PropertyNames.CONTENT_ELEMENTS);
    filterSet.add(PropertyNames.DATE_LAST_MODIFIED);
    filterSet.add(PropertyNames.MIME_TYPE);
    filterSet.add(PropertyNames.VERSION_SERIES);
    filterSet.add(PropertyNames.VERSION_SERIES_ID);
    filterSet.add(PropertyNames.RELEASED_VERSION);
    filterSet.add(PropertyNames.OWNER);
    filterSet.add(PropertyNames.PERMISSIONS);
    filterSet.add(PropertyNames.PERMISSION_TYPE);
    filterSet.add(PropertyNames.PERMISSION_SOURCE);

    StringBuilder buf = new StringBuilder();
    for (String filterName : filterSet) {
      buf.append(filterName).append(" ");
    }
    buf.deleteCharAt(buf.length() - 1);

    PropertyFilter filter = new PropertyFilter();
    filter.addIncludeProperty(
        new FilterElement(null, null, null, buf.toString(), null));
    return filter;
  }

  /**
   * Converts Distinguished Name to shortname@domain format.  If the input name
   * is in domain\shortname, domain/shortname or shortname@domain, it will not
   * do the conversion.
   * 
   * @param name string in Distinguished Name or other naming formats.
   * @return shortname@domain.com
   */
  public static String convertDn(String name) {
    if (name.toLowerCase().startsWith("cn=")) {
      String domainName = getCNFromDN(name) + "@" + getDomain(name);
      logger.log(Level.FINEST, "Convert DN {0} to {1}",
          new Object[] {name, domainName});
      return domainName;
    }
    return name;
  }

  /**
   * Extracts CN attribute from a given DN.
   * This method is copied from
   * com/google/enterprise/connector/dctm/IdentityUtil
   */
  public static String getCNFromDN(String dn) {
    if (Strings.isNullOrEmpty(dn)) {
      return null;
    }
    int pre = dn.toLowerCase().indexOf("cn=");
    int post = dn.indexOf(",", pre);
    if (pre == -1) {
      return null;
    }
    String cn;
    if (post != -1) {
      // Here 3 is length of 'cn='. We just want to add the
      // group name.
      cn = dn.substring(pre + 3, post);
    } else {
      cn = dn.substring(pre + 3);
    }
    return cn;
  }

  /**
   * Given a dn, it returns the domain.
   * E.g., DN: uid=xyz,ou=engineer,dc=abc,dc=example,dc=com
   * it will return abc.example.com
   * 
   * This method is copied from com/google/enterprise/secmgr/ldap/LDAPClient
   * and modified to exclude NETBIOS naming check.
   * 
   * @param dn the distinguished name
   * @return domain in the form abc.com, or null if the input was invalid or did
   * not contain the domain attribute
   */
  public static String getDomain(String dn) {
    if (Strings.isNullOrEmpty(dn)) {
      return null;
    }
    Iterable<String> str =
        Splitter.on(',').trimResults().omitEmptyStrings().split(dn);
    StringBuilder strBuilder = new StringBuilder();
    for (String substr : str) {
      if (substr.startsWith("dc") || substr.startsWith("DC")) {
        strBuilder.append(substr.substring(3)).append(".");
      }
    }
    String strDomain = strBuilder.toString();
    if (Strings.isNullOrEmpty(strDomain)) {
      return null;
    }
    return strDomain.substring(0, strDomain.length() - 1);
  }
}
