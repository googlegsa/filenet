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
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.GuidConstants;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.core.Document;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.util.Id;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for: 1. Construction of FileNet SQL queries for adding and
 * deleting index of documents to GSA. 2. Execution of the SQL query constructed
 * in step 1. 3. Retrieve the results of step 2 and wrap it in DocumentList
 */
class DocumentTraverser implements FileNetAdaptor.Traverser {
  private static final Logger logger =
      Logger.getLogger(DocumentTraverser.class.getName());

  private final ConfigOptions options;

  public DocumentTraverser(ConfigOptions options) {
    this.options = options;
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
          options.getMaxFeedUrls() - 1, SearchWrapper.dereferenceObjects,
          SearchWrapper.ALL_ROWS);
      logger.fine(objectSet.isEmpty()
          ? "Found no documents to add or update"
          : "Found documents to add or update");

      ArrayList<DocId> docIds = new ArrayList<>();
      Date timestamp = null;
      Id guid = null; // TODO: Id.ZERO_ID?
      Iterator<?> objects = objectSet.iterator();
      while (objects.hasNext()) {
        Document object = (Document) objects.next();
        timestamp = object.get_DateLastModified();
        guid = object.get_Id(); // TODO: Use the VersionSeries ID.
        docIds.add(newDocId(guid));
      }
      if (timestamp != null) {
        docIds.add(newDocId(new Checkpoint(checkpoint.type, timestamp, guid)));
        pusher.pushDocIds(docIds);
      }
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
    query.append(options.getMaxFeedUrls() - 1);
    query.append(" ");
    query.append(PropertyNames.ID);
    query.append(",");
    query.append(PropertyNames.DATE_LAST_MODIFIED);
    query.append(",");
    query.append(PropertyNames.RELEASED_VERSION);
    query.append(" FROM ");
    query.append(GuidConstants.Class_Document);
    query.append(" WHERE VersionStatus=1 and ContentSize IS NOT NULL ");

    String additionalWhereClause = options.getAdditionalWhereClause();
    if (additionalWhereClause != null && !additionalWhereClause.equals("")) {
      if ((additionalWhereClause.toUpperCase()).startsWith("SELECT ID,DATELASTMODIFIED FROM ")) {
        query = new StringBuilder(additionalWhereClause);
        query.replace(0, 6,
            "SELECT TOP " + (options.getMaxFeedUrls() - 1) + " ");
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
    // TODO(jlacey): This can't happen. Make sure of that and remove this.
    if (uuid.equals("")) {
      uuid = Id.ZERO_ID.toString();
    }
    logger.log(Level.FINE, "MakeCheckpointQueryString date: {0}", c);
    logger.log(Level.FINE, "MakeCheckpointQueryString ID: {0}", uuid);
    String whereClause = " AND (("
        + PropertyNames.DATE_LAST_MODIFIED + "={0} AND (''{1}''<"
        + PropertyNames.ID + ")) OR (" + PropertyNames.DATE_LAST_MODIFIED
        + ">{0}))";
    return MessageFormat.format(whereClause, new Object[] { c, uuid });
  }

  @Override
  public void getDocContent(Id guid, Request request, Response response)
      throws IOException {
    try (AutoConnection connection = options.getConnection()) {
      ObjectStore objectStore = options.getObjectStore(connection);
      logger.log(Level.FINE, "Target ObjectStore is: {0}", objectStore);

      Document document = (Document)
          objectStore.fetchObject(ClassNames.DOCUMENT, guid,
              FileUtil.getDocumentPropertyFilter(
                  options.getIncludedMetadata()));

      logger.log(Level.FINEST, "Add document [ID: {0}]", guid);
      processDocument(guid, newDocId(guid), document, response);
    } catch (EngineRuntimeException e) {
      throw new IOException(e);
    }
  }

  private void processDocument(Id guid, DocId docId, Document document,
      Response response) throws IOException {
    logger.log(Level.FINE, "Fetch document for DocId {0}", guid);
    String vsDocId = document.get_VersionSeries().get_Id().toString();
    logger.log(Level.FINE, "VersionSeriesID for document is: {0}", vsDocId);

    try {
      ActiveMarkingList activeMarkings = document.get_ActiveMarkings();
      if (!activeMarkings.isEmpty()) {
        throw new UnsupportedOperationException(
            "Document " + vsDocId + " has an active marking set.");
      }
    } catch (EngineRuntimeException e) {
      // TODO(jlacey): The IBM doc suggests this is just a property
      // filter issue, and we might get a different error if or none
      // at all if there are no active markings.
      if (e.getExceptionCode() == ExceptionCode.API_PROPERTY_NOT_IN_CACHE) {
        logger.log(Level.FINER, "Assuming no active markings: {0}",
            e.getMessage());
      } else {
        throw e;
      }
    }
    Permissions.Acl permissions =
        new Permissions(document.get_Permissions(), document.get_Owner())
        .getAcl();
    response.setAcl(getAcl(docId, permissions));
    processInheritedPermissions(docId, permissions, response);

    response.setContentType(document.get_MimeType());
    // TODO(jlacey): Use ValidatedUri. Use a percent encoder, or URI's
    // multi-argument constructors?
    response.setDisplayUrl(
        URI.create(options.getDisplayUrl()
            + vsDocId.replace("{", "%7B").replace("}", "%7D")));
    response.setLastModified(document.get_DateLastModified());
    response.setSecure(!options.markAllDocsAsPublic());

    setMetadata(document, response);

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

  private Acl getAcl(DocId docId, Permissions.Acl permissions) {
    return createAcl(docId, getParentFragment(permissions),
        Acl.InheritanceType.LEAF_NODE, options.getGlobalNamespace(),
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
  }

  public static final String SEC_POLICY_POSTFIX = "TMPL";
  public static final String SEC_FOLDER_POSTFIX = "FLDR";

  private String getParentFragment(Permissions.Acl permissions) {
    if (hasPermissions(permissions, PermissionSource.SOURCE_TEMPLATE)) {
      return SEC_POLICY_POSTFIX;
    } else if (hasPermissions(permissions, PermissionSource.SOURCE_PARENT)) {
      return SEC_FOLDER_POSTFIX;
    } else {
      return null;
    }
  }

  private boolean hasPermissions(Permissions.Acl permissions,
      PermissionSource permSrc) {
    return !(permissions.getAllowUsers(permSrc).isEmpty()
        && permissions.getDenyUsers(permSrc).isEmpty()
        && permissions.getAllowGroups(permSrc).isEmpty()
        && permissions.getDenyGroups(permSrc).isEmpty());
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

  // TODO(jlacey): Should we always include the full ACL chain, and the
  // security template in particular, so that SecurityPolicyTraverser can
  // update just the one ACL and not have to rewire the chain?
  private void processInheritedPermissions(DocId docId,
      Permissions.Acl permissions, Response response) {
    // Send add request for adding ACLs inherited from parent folders.
    String secParentFragment = null;
    Acl folderAcl =
        createAcl(docId, permissions, PermissionSource.SOURCE_PARENT, null);
    if (folderAcl == null) {
      logger.log(Level.FINEST,
          "{0} does not have inherited folder permissions", docId);
    } else {
      logger.log(Level.FINEST, "Create ACL for folder {0}#{1}: {2}",
          new Object[] {docId, SEC_FOLDER_POSTFIX, folderAcl});
      response.putNamedResource(SEC_FOLDER_POSTFIX, folderAcl);
      secParentFragment = SEC_FOLDER_POSTFIX;
    }

    // Send add request for adding ACLs inherited from security template.
    Acl secAcl = createAcl(docId, permissions, PermissionSource.SOURCE_TEMPLATE,
        secParentFragment);
    if (secAcl == null) {
      logger.log(Level.FINEST,
          "{0} does not have inherited template permissions", docId);
    } else {
      logger.log(Level.FINEST,
          "Create ACL for security template {0}#{1}: {2}",
          new Object[] {docId, SEC_POLICY_POSTFIX, secAcl});
      response.putNamedResource(SEC_POLICY_POSTFIX, secAcl);
    }
  }

  private Acl createAcl(DocId docId, Permissions.Acl permissions,
      PermissionSource permSrc, String parentFragment) {
    Set<String> allowUsers = permissions.getAllowUsers(permSrc);
    Set<String> denyUsers = permissions.getDenyUsers(permSrc);
    Set<String> allowGroups = permissions.getAllowGroups(permSrc);
    Set<String> denyGroups = permissions.getDenyGroups(permSrc);
    if (allowUsers.isEmpty() && denyUsers.isEmpty()
        && allowGroups.isEmpty() && denyGroups.isEmpty()) {
      return null;
    } else {
      return createAcl(docId, parentFragment,
          Acl.InheritanceType.CHILD_OVERRIDES,
          options.getGlobalNamespace(), allowUsers, denyUsers,
          allowGroups, denyGroups);
    }
  }

  private Acl createAcl(DocId docId, String parentFragment,
      Acl.InheritanceType inheritanceType, String namespace,
      Set<String> allowUsers, Set<String> denyUsers,
      Set<String> allowGroups, Set<String> denyGroups) {
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
    IDocumentProperties properties =
        options.getObjectFactory().getDocumentProperties(document);
    for (String name : getPropertyNames(properties)) {
      ArrayList<String> list = new ArrayList<>();
      properties.getProperty(name, list);
      for (String value : list) {
        response.addMetadata(name, value);
      }
    }
  }

  private Set<String> getPropertyNames(IDocumentProperties properties)  {
    Set<String> names = new HashSet<String>();
    for (String property : properties.getPropertyNames()) {
      if (property != null) {
        if (options.getIncludedMetadata().size() != 0) {
          // includeMeta - excludeMeta
          if ((!options.getExcludedMetadata().contains(property)
              && options.getIncludedMetadata().contains(property))) {
            names.add(property);
          }
        } else {
          // superSet - excludeMeta
          if ((!options.getExcludedMetadata().contains(property))) {
            names.add(property);
          }
        }
      }
    }
    logger.log(Level.FINEST, "Property names: {0}", properties);
    return names;
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
          logger.log(Level.FINEST, "{0} : {1}",
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
