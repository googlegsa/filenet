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
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.InvalidConfigurationException;
import com.google.enterprise.adaptor.SensitiveValueDecoder;

import com.filenet.api.core.ObjectStore;

import java.net.URISyntaxException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO(bmj): move this into FileNetAdaptor?
class ConfigOptions {
  private static final Logger logger =
      Logger.getLogger(ConfigOptions.class.getName());

  private static final Splitter splitter =
      Splitter.on(',').omitEmptyStrings().trimResults();

  private final SensitiveValueDecoder sensitiveValueDecoder;
  private final String contentEngineUrl;
  private final String username;
  private final String password;
  private final String objectStoreName;

  private final ObjectFactory objectFactory;
  private final String displayUrl;
  private final boolean markAllDocsAsPublic;
  private final String additionalWhereClause;
  private final String deleteAdditionalWhereClause;
  private final Set<String> includedMetadata;
  private final Set<String> excludedMetadata;
  private final String globalNamespace;
  private final int maxFeedUrls;

  public ConfigOptions(AdaptorContext context)
      throws InvalidConfigurationException {
    Config config = context.getConfig();
    sensitiveValueDecoder = context.getSensitiveValueDecoder();

    contentEngineUrl = config.getValue("filenet.contentEngineUrl");
    logger.log(Level.CONFIG, "filenet.contentEngineUrl: {0}", contentEngineUrl);
    try {
      new ValidatedUri(contentEngineUrl).logUnreachableHost();
    } catch (URISyntaxException e) {
      throw new InvalidConfigurationException(
          "Invalid filenet.contentEngineUrl: " + e.getMessage());
    }

    objectStoreName = config.getValue("filenet.objectStore");
    if (objectStoreName.isEmpty()) {
      throw new InvalidConfigurationException(
          "filenet.objectStore may not be empty");
    }
    logger.log(Level.CONFIG, "filenet.objectStore: {0}", objectStoreName);

    username = config.getValue("filenet.username");
    password = config.getValue("filenet.password");

    String objectFactoryName = config.getValue("filenet.objectFactory");
    if (objectFactoryName.isEmpty()) {
      throw new InvalidConfigurationException(
          "filenet.objectFactory may not be empty");
    }
    try {
      objectFactory =
          (ObjectFactory) Class.forName(objectFactoryName).newInstance();
    } catch (ClassNotFoundException | InstantiationException
             | IllegalAccessException e) {
      throw new InvalidConfigurationException(
          "Unable to instantiate object factory: " + objectFactoryName, e);
    }
    logger.log(Level.CONFIG, "filenet.objectFactory: {0}", objectFactoryName);

    String workplaceUrl = config.getValue("filenet.displayUrl");
    if (workplaceUrl.endsWith("/getContent/")) {
      workplaceUrl = workplaceUrl.substring(0, workplaceUrl.length() - 1);
    }
    if (workplaceUrl.contains("/getContent")
        && workplaceUrl.endsWith("/getContent")) {
      workplaceUrl += "?objectStoreName=" + objectStoreName
          + "&objectType=document&versionStatus=1&vsId=";
    } else {
      workplaceUrl += "/getContent?objectStoreName=" + objectStoreName
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

    markAllDocsAsPublic =
        Boolean.parseBoolean(config.getValue("adaptor.markAllDocsAsPublic"));
    logger.log(Level.CONFIG, "adaptor.markAllDocsAsPublic: {0}",
        markAllDocsAsPublic);

    globalNamespace = config.getValue("adaptor.namespace");
    logger.log(Level.CONFIG, "adaptor.namespace: {0}", globalNamespace);

    // TODO(bmj): validate where clauses
    additionalWhereClause = config.getValue("filenet.additionalWhereClause");
    logger.log(Level.CONFIG, "filenet.additionalWhereClause: {0}",
        additionalWhereClause);
    deleteAdditionalWhereClause =
        config.getValue("filenet.deleteAdditionalWhereClause");
    logger.log(Level.CONFIG, "filenet.deleteAdditionalWhereClause: {0}",
        deleteAdditionalWhereClause);

    // TODO(bmj): validate column names?
    excludedMetadata = ImmutableSet.copyOf(
        splitter.split(config.getValue("filenet.excludedMetadata")));
    logger.log(Level.CONFIG, "filenet.excludedMetadata: {0}", excludedMetadata);

    // TODO(bmj): validate column names?
    includedMetadata = ImmutableSet.copyOf(
        splitter.split(config.getValue("filenet.includedMetadata")));
    logger.log(Level.CONFIG, "filenet.includedMetadata: {0}", includedMetadata);

    try {
      maxFeedUrls = Integer.parseInt(config.getValue("feed.maxUrls"));
      if (maxFeedUrls < 2) {
        throw new InvalidConfigurationException(
            "feed.maxUrls must be greater than 1: " + maxFeedUrls);
      }
    } catch (NumberFormatException e) {
      throw new InvalidConfigurationException(
          "Invalid feed.maxUrls value: " + config.getValue("feed.maxUrls"));
    }
    logger.log(Level.CONFIG, "feed.maxUrls: {0}", maxFeedUrls);
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

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public Connection getConnection() {
    return objectFactory.getConnection(contentEngineUrl, username,
        sensitiveValueDecoder.decodeValue(password));
  }

  public ObjectStore getObjectStore(Connection connection) {
    return objectFactory.getObjectStore(connection, objectStoreName);
  }

  public String getDisplayUrl() {
    return displayUrl;
  }

  public boolean markAllDocsAsPublic() {
    return markAllDocsAsPublic;
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
