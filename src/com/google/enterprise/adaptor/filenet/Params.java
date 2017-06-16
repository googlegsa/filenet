// Copyright 2017 Google Inc. All Rights Reserved.
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
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.InvalidConfigurationException;

import java.net.URISyntaxException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO(bmj): move this into FileNetAdaptor?
class Params {
  private static final Logger logger =
      Logger.getLogger(Params.class.getName());

  private static final Splitter splitter =
      Splitter.on(',').omitEmptyStrings().trimResults();

  private final String contentEngineUrl;
  private final String username;
  private final String password;
  private final String objectStore;

  private final String objectFactory;
  private final String displayUrl;
  private final boolean isPublic;
  private final boolean pushAcls;
  private final String additionalWhereClause;
  private final String deleteAdditionalWhereClause;
  private final Set<String> includedMetadata;
  private final Set<String> excludedMetadata;
  private final String globalNamespace;
  private final int maxFeedUrls;

  public Params(Config config) throws InvalidConfigurationException {
    contentEngineUrl = config.getValue("filenet.contentEngineUrl");
    logger.log(Level.CONFIG, "filenet.contentEngineUrl: {0}", contentEngineUrl);
    try {
      new ValidatedUri(contentEngineUrl).logUnreachableHost();
    } catch (URISyntaxException e) {
      throw new InvalidConfigurationException(
          "Invalid filenet.contentEngineUrl: " + e.getMessage());
    }

    objectStore = config.getValue("filenet.objectStore").trim();
    if (objectStore.isEmpty()) {
      throw new InvalidConfigurationException("filenet.objectStore may not be empty");
    }
    logger.log(Level.CONFIG, "filenet.objectStore: {0}", objectStore);

    username = config.getValue("filenet.username");
    password = config.getValue("filenet.password");

    // TODO(bmj): load the object factor?
    objectFactory = config.getValue("filenet.objectFactory").trim();
    if (objectFactory.isEmpty()) {
      throw new InvalidConfigurationException(
          "filenet.objectFactory may not be empty");
    }
    logger.log(Level.CONFIG, "filenet.objectFactory: {0}", objectFactory);

    // TODO(bmj): If empty, can I base it off of contentEngineUrl?
    String workplaceUrl = config.getValue("filenet.displayUrl").trim();
    if (workplaceUrl.endsWith("/getContent/")) {
      workplaceUrl = workplaceUrl.substring(0, workplaceUrl.length() - 1);
    }
    if (workplaceUrl.contains("/getContent")
        && workplaceUrl.endsWith("/getContent")) {
      workplaceUrl += "?objectStoreName=" + objectStore
          + "&objectType=document&versionStatus=1&vsId=";
    } else {
      workplaceUrl += "/getContent?objectStoreName=" + objectStore
          + "&objectType=document&versionStatus=1&vsId=";
    }
    displayUrl = workplaceUrl;
    logger.log(Level.CONFIG, "displayUrl: {0}", displayUrl);
    try {
      new ValidatedUri(displayUrl + "0").logUnreachableHost();
    } catch (URISyntaxException e) {
      throw new InvalidConfigurationException(
          "Invalid displayUrl: " + e.getMessage());
    }

    isPublic = Boolean.parseBoolean(config.getValue("filenet.isPublic").trim());
    logger.log(Level.CONFIG, "filenet.isPublic: " + isPublic);

    String pushAclsStr = config.getValue("filenet.pushAcls").trim();
    pushAcls = pushAclsStr.isEmpty() ? true : Boolean.parseBoolean(pushAclsStr);
    logger.log(Level.CONFIG, "filenet.pushAcls: " + pushAcls);

    globalNamespace = config.getValue("adaptor.namespace").trim();
    logger.log(Level.CONFIG, "adaptor.namespace: " + globalNamespace);

    // TODO(bmj): validate where clauses
    additionalWhereClause = config.getValue("filenet.additionalWhereClause");
    logger.log(Level.CONFIG, "filenet.additionalWhereClause: "
        + additionalWhereClause);
    deleteAdditionalWhereClause =
        config.getValue("filenet.deleteAdditionalWhereClause");
    logger.log(Level.CONFIG, "filenet.deleteAdditionalWhereClause: "
        + deleteAdditionalWhereClause);

    // TODO(bmj): validate column names?
    excludedMetadata = ImmutableSet.copyOf(
        splitter.split(config.getValue("filenet.excludedMetadata")));
    logger.log(Level.CONFIG, "filenet.excludedMetadata: " + excludedMetadata);

    // TODO(bmj): validate column names?
    includedMetadata = ImmutableSet.copyOf(
        splitter.split(config.getValue("filenet.includedMetadata")));
    logger.log(Level.CONFIG, "filenet.includedMetadata: " + includedMetadata);

    try {
      maxFeedUrls = Integer.parseInt(config.getValue("feed.maxUrls").trim());
      if (maxFeedUrls < 2) {
        throw new InvalidConfigurationException(
            "feed.maxUrls must be greater than 1: " + maxFeedUrls);
      }
    } catch (NumberFormatException e) {
      throw new InvalidConfigurationException(
          "Invalid feed.maxUrls value: " + config.getValue("feed.maxUrls"));
    }
  }

  public String getContentEngineUrl() {
    return contentEngineUrl;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getObjectStore() {
    return objectStore;
  }

  // TODO(bmj): have this return the actual object factory?
  public String getObjectFactory() {
    return objectFactory;
  }

  public String getDisplayUrl() {
    return displayUrl;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public boolean pushAcls() {
    return pushAcls;
  }

  public String getGlobalNamespace() {
    return globalNamespace;
  }

  public String getAdditionalWhereClause() {
    return additionalWhereClause;
  }

  public String getDeleteAdditionalWhereClause() {
    return deleteAdditionalWhereClause;
  }

  public Set<String> getExcludedMetadata() {
    return excludedMetadata;
  }

  public Set<String> getIncludedMetadata() {
    return includedMetadata;
  }

  public int getMaxFeedUrls() {
    return maxFeedUrls;
  }
}
