// Copyright 2008 Google Inc. All Rights Reserved.
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

import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A mockable wrapper on {@code SearchScope}, which is a final class.
 * This class does not expose SQLSearch, but that it could. That class
 * is usable by the mock tests, but since we always use full query
 * strings, it does not add value for the callers.
 */
class SearchWrapper {
  private static final Logger logger =
      Logger.getLogger(SearchWrapper.class.getName());

  /** Do not filter the results or deference object properties. */
  public static final PropertyFilter NO_FILTER = null;

  /** Page through larger, sorted results. */
  public static final Boolean CONTINUABLE = Boolean.TRUE;

  /** Do not page through small or unsorted results. */
  public static final Boolean NOT_CONTINUABLE = Boolean.FALSE;

  private final SearchScope search;

  /** For mocks. */
  protected SearchWrapper() {
    this(null);
  }

  public SearchWrapper(SearchScope search) {
    this.search = search;
  }

  public IndependentObjectSet fetchObjects(String query, Integer pageSize,
      PropertyFilter filter, Boolean continuable) {
    logger.log(Level.FINEST, "Execute query: {0}", query);
    return search.fetchObjects(new SearchSQL(query), pageSize, filter,
        continuable);
  }
}
