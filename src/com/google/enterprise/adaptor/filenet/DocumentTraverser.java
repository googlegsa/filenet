// Copyright 2007 Google Inc. All Rights Reserved.
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

import static com.google.common.collect.Sets.union;
import static com.google.enterprise.adaptor.filenet.FileNetAdaptor.newDocId;

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.IOHelper;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.adaptor.filenet.FileNetAdaptor.Checkpoint;

import com.filenet.api.collection.ActiveMarkingList;
import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.constants.GuidConstants;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.core.Document;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.property.Property;
import com.filenet.api.property.PropertyBoolean;
import com.filenet.api.property.PropertyBooleanList;
import com.filenet.api.property.PropertyDateTime;
import com.filenet.api.property.PropertyDateTimeList;
import com.filenet.api.property.PropertyFloat64;
import com.filenet.api.property.PropertyFloat64List;
import com.filenet.api.property.PropertyId;
import com.filenet.api.property.PropertyIdList;
import com.filenet.api.property.PropertyInteger32;
import com.filenet.api.property.PropertyInteger32List;
import com.filenet.api.property.PropertyString;
import com.filenet.api.property.PropertyStringList;
import com.filenet.api.security.ActiveMarking;
import com.filenet.api.security.Marking;
import com.filenet.api.util.Id;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches batches of documents from checkpoints, and individual
 * documents given their VersionSeries ID. The VersionSeries ID is
 * used instead of the Document ID because it remains the same for all
 * versions of a document.
 */
class DocumentTraverser implements FileNetAdaptor.Traverser {
  private static final Logger logger =
      Logger.getLogger(DocumentTraverser.class.getName());

  private final ConfigOptions options;
  private final int maxRecords;

  public DocumentTraverser(ConfigOptions options) {
    this.options = options;
    // Leave room for the continuation URLs.
    this.maxRecords = options.getMaxFeedUrls() - 2;
  }

  /** Percent escapes the curly braces in an Id string. */
  @VisibleForTesting
  static String percentEscape(String id) {
    return id.replace("{", "%7B").replace("}", "%7D");
  }

  @Override
  public void getDocIds(Checkpoint checkpoint, DocIdPusher pusher)
      throws IOException, InterruptedException {
    try (AutoConnection connection = options.getConnection()) {
      ObjectStore objectStore = options.getObjectStore(connection);
      logger.log(Level.FINE, "Target ObjectStore is: {0}", objectStore);

      ObjectFactory objectFactory = options.getObjectFactory();
      SearchWrapper search = objectFactory.getSearch(objectStore);

      String query = buildQueryString(checkpoint);
      logger.log(Level.FINE, "Query for added or updated documents: {0}",
          query);
      IndependentObjectSet objectSet = search.fetchObjects(query,
          maxRecords, SearchWrapper.dereferenceObjects,
          SearchWrapper.CONTINUABLE);
      logger.fine(objectSet.isEmpty()
          ? "Found no documents to add or update"
          : "Found documents to add or update");

      ArrayList<DocIdPusher.Record> records = new ArrayList<>();
      Date timestamp = null;
      Id guid = null;
      Iterator<?> objects = objectSet.iterator();
      while (objects.hasNext()) {
        Document object = (Document) objects.next();
        timestamp = object.get_DateLastModified();
        guid = object.get_Id();
        Id vsId = object.get_VersionSeries().get_Id();
        logger.log(Level.FINER, "Document ID: {0}", guid);
        logger.log(Level.FINER, "VersionSeries ID: {0}", vsId);
        records.add(new DocIdPusher.Record.Builder(newDocId(vsId)).build());
      }
      // TODO(jlacey): if (records.size() == maxRecords)
      if (timestamp != null) {
        records.add(
            new DocIdPusher.Record.Builder(
                newDocId(new Checkpoint(checkpoint.type, timestamp, guid)))
            .setCrawlImmediately(true).build());
      }
      records.add(
          new DocIdPusher.Record.Builder(newDocId(checkpoint))
          .setDeleteFromIndex(true).build());
      pusher.pushRecords(records);
    } catch (EngineRuntimeException e) {
      throw new IOException(e);
    }
  }

  /**
   * To construct FileNet query to fetch documents from FileNet repository
   * considering additional where clause specified as connector
   * configuration and the previously remembered checkpoint to indicate where
   * to resume acquiring documents from the FileNet repository to send feed.
   */
  private String buildQueryString(Checkpoint checkpoint) {
    StringBuilder query = new StringBuilder("SELECT TOP ");
    query.append(maxRecords);
    query.append(" ");
    query.append(PropertyNames.ID);
    query.append(",");
    query.append(PropertyNames.DATE_LAST_MODIFIED);
    query.append(",");
    query.append(PropertyNames.VERSION_SERIES);
    query.append(" FROM ");
    query.append(GuidConstants.Class_Document);
    query.append(" WHERE VersionStatus=1 and ContentSize IS NOT NULL ");

    String additionalWhereClause = options.getAdditionalWhereClause();
    if (additionalWhereClause != null && !additionalWhereClause.equals("")) {
      if ((additionalWhereClause.toUpperCase()).startsWith("SELECT ID,DATELASTMODIFIED FROM ")) {
        query = new StringBuilder(additionalWhereClause);
        query.replace(0, 6,
            "SELECT TOP " + maxRecords + " ");
        logger.log(Level.FINE, "Using Custom Query[{0}]",
            additionalWhereClause);
      } else {
        query.append(additionalWhereClause);
      }
    }
    if (!checkpoint.isEmpty()) {
      query.append(getCheckpointClause(checkpoint));
    }
    query.append(" ORDER BY ");
    query.append(PropertyNames.DATE_LAST_MODIFIED);
    query.append(",");
    query.append(PropertyNames.ID);
    return query.toString();
  }

  /**
   * Returns a query string for the checkpoint values.
   *
   * @param checkpoint the checkpoint
   * @param dateField the checkpoint date field
   * @param uuidField the checkpoint ID field
   * @return a query string
   * @throws RepositoryException if the checkpoint is uninitialized
   */
  @VisibleForTesting
  String getCheckpointClause(Checkpoint checkPoint) {
    String c = checkPoint.timestamp;
    String uuid = checkPoint.guid;
    logger.log(Level.FINE, "MakeCheckpointQueryString date: {0}", c);
    logger.log(Level.FINE, "MakeCheckpointQueryString ID: {0}", uuid);
    String whereClause = " AND (("
        + PropertyNames.DATE_LAST_MODIFIED + "={0} AND (''{1}''<"
        + PropertyNames.ID + ")) OR (" + PropertyNames.DATE_LAST_MODIFIED
        + ">{0}))";
    return MessageFormat.format(whereClause, new Object[] { c, uuid });
  }

  @Override
  public void getDocContent(Id vsId, Request request, Response response)
      throws IOException {
    try (AutoConnection connection = options.getConnection()) {
      ObjectStore objectStore = options.getObjectStore(connection);
      logger.log(Level.FINE, "Target ObjectStore is: {0}", objectStore);

      ObjectFactory objectFactory = options.getObjectFactory();
      SearchWrapper search = objectFactory.getSearch(objectStore);

      String query = "SELECT Id FROM Document"
          + " WHERE VersionSeries = OBJECT(" + vsId + ") and VersionStatus = 1";
      IndependentObjectSet objectSet = search.fetchObjects(query, null,
          SearchWrapper.NO_FILTER, SearchWrapper.NOT_CONTINUABLE);
      Iterator<?> objects = objectSet.iterator();
      if (objects.hasNext()) {
        Document document = (Document) objects.next();
        Id guid = document.get_Id();
        logger.log(Level.FINE, "Found document ID {0} for VersionSeries ID {1}",
            new Object[] { guid, vsId });
        try {
          document.refresh(
              FileUtil.getDocumentPropertyFilter(
                  options.getIncludedMetadata(),
                  options.getExcludedMetadata()));
        } catch (EngineRuntimeException e) {
          // This inner try statement just avoids returning 404 if the
          // object store is not found (returning 500 instead).
          if (e.getExceptionCode() == ExceptionCode.E_OBJECT_NOT_FOUND) {
            logger.log(Level.FINE, "Unable to fetch document for ID {0}", guid);
            response.respondNotFound();
            return;
          } else {
            throw e;
          }
        }

        processDocument(newDocId(vsId), vsId, guid, document,
            request, response);

        if (objects.hasNext()) {
          logger.log(Level.INFO,
              "Duplicate released documents for VersionSeries ID: {0}", vsId);
        }
      } else {
        logger.log(Level.FINE, "VersionSeries not found for ID {0}", vsId);
        response.respondNotFound();
      }
    } catch (EngineRuntimeException e) {
      throw new IOException(e);
    }
  }

  private void processDocument(DocId docId, Id vsId, Id guid, Document document,
      Request request, Response response) throws IOException {
    logger.log(Level.FINE, "Process document for ID: {0}", guid);

    if (!options.markAllDocsAsPublic()) {
      ActiveMarkingList activeMarkings = document.get_ActiveMarkings();
      Permissions.Acl permissions =
          new Permissions(document.get_Permissions(), document.get_Owner())
          .getAcl();
      processPermissions(docId, activeMarkings, permissions, response);
    }

    response.setContentType(document.get_MimeType());
    response.setDisplayUrl(
        URI.create(options.getDisplayUrl() + percentEscape(vsId.toString())));
    response.setLastModified(document.get_DateLastModified());

    setMetadata(document, response);

    // Check for If-Modified-Since. The repository does not change
    // the last modified time if the ACL or metadata change, so we
    // always return those, but can skip the content if unchanged.
    if (request.canRespondWithNoContent(document.get_DateLastModified())) {
      logger.log(Level.FINE, "Content not modified since last crawl: {0}",
          guid);
      response.respondNoContent();
      return;
    }

    logger.log(Level.FINEST, "Getting content");
    if (hasAllowableSize(guid, document)) {
      try (InputStream in = document.accessContentStream(0)) {
        OutputStream out = response.getOutputStream();
        IOHelper.copyStream(in, out);
      } catch (EngineRuntimeException e) {
        if (e.getExceptionCode() == ExceptionCode.API_INDEX_OUT_OF_BOUNDS) {
          logger.log(Level.FINER, "Document has no content: {0}", guid);
          response.getOutputStream();
        } else if (e.getExceptionCode()
            == ExceptionCode.API_NOT_A_CONTENT_TRANSFER) {
          logger.log(Level.FINER,
              "Document content element is unsupported: {1}", guid);
          response.getOutputStream();
        } else {
            logger.log(Level.WARNING,
                "Unable to get document content: {1}", guid);
            response.respondNoContent();
        }
      }
    }
  }

  private List<UserPrincipal> getUserPrincipals(Set<String> names,
      String namespace) {
    ArrayList<UserPrincipal> list = new ArrayList<>();
    for (String name : names) {
      list.add(new UserPrincipal(FileUtil.convertDn(name), namespace));
    }
    return list;
  }

  private List<GroupPrincipal> getGroupPrincipals(Set<String> names,
      String namespace) {
    ArrayList<GroupPrincipal> list = new ArrayList<>();
    for (String name : names) {
      list.add(new GroupPrincipal(FileUtil.convertDn(name), namespace));
    }
    return list;
  }

  private static final String SEC_MARKING_POSTFIX = "MARK";
  private static final String SEC_POLICY_POSTFIX = "TMPL";
  private static final String SEC_FOLDER_POSTFIX = "FLDR";

  private void processPermissions(DocId docId,
      ActiveMarkingList activeMarkings, Permissions.Acl permissions,
      Response response) {
    String fragment = null;

    // Send add requests for adding ACLs inherited from ActiveMarkings.
    // The ActiveMarking ACLs must be ANDed with all the other ACLs.
    // The GSA supports AND-BOTH-PERMIT only at the root of the ACL
    // inheritance chain.
    Iterator<?> iterator = activeMarkings.iterator();
    while (iterator.hasNext()) {
      ActiveMarking activeMarking = (ActiveMarking) iterator.next();
      Marking marking = activeMarking.get_Marking();
      Permissions.Acl markingPerms =
          new Permissions(marking.get_Permissions()).getAcl();
      Acl markingAcl = createAcl(docId, fragment,
          Acl.InheritanceType.AND_BOTH_PERMIT,
          markingPerms.getAllowUsers(), markingPerms.getDenyUsers(),
          markingPerms.getAllowGroups(), markingPerms.getDenyGroups());
      fragment = SEC_MARKING_POSTFIX
          + percentEscape(marking.get_Id().toString());
      logger.log(Level.FINEST, "Create ACL for active marking {0} {1}#{2}: {3}",
          new Object[] {activeMarking.get_PropertyDisplayName(), docId,
              fragment, markingAcl});
      response.putNamedResource(fragment, markingAcl);
    }

    // Send add request for adding ACLs inherited from parent folders.
    Acl folderAcl = createAcl(docId, permissions,
        PermissionSource.SOURCE_PARENT, fragment);
    fragment = SEC_FOLDER_POSTFIX;
    logger.log(Level.FINEST, "Create ACL for folder {0}#{1}: {2}",
        new Object[] {docId, fragment, folderAcl});
    response.putNamedResource(fragment, folderAcl);

    // Send add request for adding ACLs inherited from security template.
    Acl secAcl = createAcl(docId, permissions, PermissionSource.SOURCE_TEMPLATE,
        fragment);
    fragment = SEC_POLICY_POSTFIX;
    logger.log(Level.FINEST,
        "Create ACL for security template {0}#{1}: {2}",
        new Object[] {docId, fragment, secAcl});
    response.putNamedResource(fragment, secAcl);

    // Set the direct and default document ACL.
    Acl docAcl = createAcl(docId, fragment, Acl.InheritanceType.LEAF_NODE,
        union(
            permissions.getAllowUsers(PermissionSource.SOURCE_DEFAULT),
            permissions.getAllowUsers(PermissionSource.SOURCE_DIRECT)),
        union(
            permissions.getDenyUsers(PermissionSource.SOURCE_DEFAULT),
            permissions.getDenyUsers(PermissionSource.SOURCE_DIRECT)),
        union(
            permissions.getAllowGroups(PermissionSource.SOURCE_DEFAULT),
            permissions.getAllowGroups(PermissionSource.SOURCE_DIRECT)),
        union(
            permissions.getDenyGroups(PermissionSource.SOURCE_DEFAULT),
            permissions.getDenyGroups(PermissionSource.SOURCE_DIRECT)));
    logger.log(Level.FINEST, "Create ACL for direct permissions of {0}: {1}",
        new Object[] {docId, docAcl});
    response.setAcl(docAcl);
  }

  private Acl createAcl(DocId docId, Permissions.Acl permissions,
      PermissionSource permSrc, String parentFragment) {
    return createAcl(docId, parentFragment, Acl.InheritanceType.CHILD_OVERRIDES,
        permissions.getAllowUsers(permSrc),
        permissions.getDenyUsers(permSrc),
        permissions.getAllowGroups(permSrc),
        permissions.getDenyGroups(permSrc));
  }

  private Acl createAcl(DocId docId, String parentFragment,
      Acl.InheritanceType inheritanceType,
      Set<String> allowUsers, Set<String> denyUsers,
      Set<String> allowGroups, Set<String> denyGroups) {
    String namespace = options.getGlobalNamespace();
    Acl.Builder builder = new Acl.Builder();
    builder.setEverythingCaseInsensitive();
    builder.setInheritanceType(inheritanceType);
    builder.setPermitUsers(getUserPrincipals(allowUsers, namespace));
    builder.setDenyUsers(getUserPrincipals(denyUsers, namespace));
    builder.setPermitGroups(getGroupPrincipals(allowGroups, namespace));
    builder.setDenyGroups(getGroupPrincipals(denyGroups, namespace));
    if (parentFragment != null) {
      builder.setInheritFrom(docId, parentFragment);
    }
    return builder.build();
  }

  private void setMetadata(Document document, Response response) {
    Set<String> names = new TreeSet<>();
    for (Property property : document.getProperties().toArray()) {
      String name = property.getPropertyName();
      if ((options.getIncludedMetadata().isEmpty()
              || options.getIncludedMetadata().contains(name))
          && !options.getExcludedMetadata().contains(name)) {
        logger.log(Level.FINEST, "Getting property: [{0}]", name);
        for (String value : getPropertyValues(property)) {
          response.addMetadata(name, value);
          names.add(name);
        }
      }
    }
    logger.log(Level.FINEST, "Property names: {0}", names);
  }

  private List<String> getPropertyValues(Property prop) {
    ArrayList<String> list = new ArrayList<>();
    if (prop instanceof PropertyString) {
      getValue(prop.getStringValue(), list);
    } else if (prop instanceof PropertyStringList) {
      getListValue(prop.getStringListValue(), list);
    } else if (prop instanceof PropertyBoolean) {
      getValue(prop.getBooleanValue(), list);
    } else if (prop instanceof PropertyBooleanList) {
      getListValue(prop.getBooleanListValue(), list);
    } else if (prop instanceof PropertyDateTime) {
      getDateValue(prop.getDateTimeValue(), list);
    } else if (prop instanceof PropertyDateTimeList) {
      getDateListValue(prop.getDateTimeListValue(), list);
    } else if (prop instanceof PropertyFloat64) {
      getValue(prop.getFloat64Value(), list);
    } else if (prop instanceof PropertyFloat64List) {
      getListValue(prop.getFloat64ListValue(), list);
    } else if (prop instanceof PropertyInteger32) {
      getValue(prop.getInteger32Value(), list);
    } else if (prop instanceof PropertyInteger32List) {
      getListValue(prop.getInteger32ListValue(), list);
    } else if (prop instanceof PropertyId) {
      getGuidValue(prop.getIdValue(), list);
    } else if (prop instanceof PropertyIdList) {
      getGuidListValue(prop.getIdListValue(), list);
    } else {
      logger.log(Level.FINEST, "Property type is not supported: {0}",
          prop.getClass().getName());
    }
    return list;
  }

  private void getValue(Object val, List<String> valuesList) {
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getListValue(List<?> values, List<String> valuesList) {
    for (Object val : values) {
      getValue(val, valuesList);
    }
  }

  private void getGuidValue(Id val, List<String> valuesList) {
    if (val != null) {
      String id = val.toString();
      valuesList.add(id.substring(1, id.length() - 1));
    }
  }

  private void getGuidListValue(List<?> values, List<String> valuesList) {
    for (Object val : values) {
      getGuidValue((Id) val, valuesList);
    }
  }

  private void getDateValue(Date val, List<String> valuesList) {
    if (val != null) {
      valuesList.add(options.getMetadataDateFormat().format(val));
    }
  }

  private void getDateListValue(List<?> values, List<String> valuesList) {
    for (Object val : values) {
      getDateValue((Date) val, valuesList);
    }
  }

  private boolean hasAllowableSize(Id guid, Document document) {
    Double value = document.get_ContentSize();
    if (value == null) {
      logger.log(Level.FINEST,
          "Send content to the GSA since the {0} value is null [DocId: {1}]",
          new Object[] {PropertyNames.CONTENT_SIZE, guid});
      return true;
    } else {
      double contentSize = value.doubleValue();
      if (contentSize > 0) {
        if (contentSize <= 2L * 1024 * 1024 * 1024) {
          logger.log(Level.FINEST, "Property {0}: {1}",
              new Object[] {PropertyNames.CONTENT_SIZE, contentSize});
          return true;
        } else {
          logger.log(Level.FINER,
              "{0} [{1}] exceeds the allowable size [DocId: {2}]",
              new Object[] {PropertyNames.CONTENT_SIZE, contentSize, guid});
          return false;
        }
      } else {
        logger.log(Level.FINEST, "{0} is empty [DocId: {1}]",
            new Object[] {PropertyNames.CONTENT_SIZE, guid});
        return false;
      }
    }
  }
}
