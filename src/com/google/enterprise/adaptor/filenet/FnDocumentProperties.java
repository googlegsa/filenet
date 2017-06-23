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

import com.filenet.api.core.Document;
import com.filenet.api.property.Properties;
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
import com.filenet.api.util.Id;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class FnDocumentProperties implements IDocumentProperties {
  private static final Logger logger =
      Logger.getLogger(FnDocumentProperties.class.getName());

  private final Document doc;
  private final Map<String, Property> metas;

  public FnDocumentProperties(Document doc) {
    this.doc = doc;
    this.metas = getMetas();
  }

  private Map<String, Property> getMetas() {
    Map<String, Property> propMap = new HashMap<>();
    Properties props = doc.getProperties();
    Property[] propList = props.toArray();
    for (Property property : propList) {
      propMap.put(property.getPropertyName(), property);
    }
    return propMap;
  }

  @Override
  public Set<String> getPropertyNames() {
    return metas.keySet();
  }

  @Override
  public void getProperty(String name, List<String> list) {
    logger.log(Level.FINEST, "Getting property: [{0}]", name);
    Property prop = metas.get(name);
    if (prop == null) {
      logger.log(Level.FINEST, "Property not found: {0}", name);
      return;
    }
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
  }

  private void getValue(Object val, List<String> valuesList) {
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getListValue(List<?> values, List<String> valuesList) {
    for (Object val : values) {
      if (val != null) {
        valuesList.add(val.toString());
      }
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
      if (val != null) {
        String id = val.toString();
        valuesList.add(id.substring(1, id.length() - 1));
      }
    }
  }

  private void getDateValue(Date val, List<String> valuesList) {
    if (val != null) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(val);
      valuesList.add(/*TODO: ISO 8601*/val.toString());
    }
  }

  private void getDateListValue(List<?> values, List<String> valuesList) {
    for (Object val : values) {
      if (val != null) {
        Calendar cal = Calendar.getInstance();
        cal.setTime((Date) val);
        valuesList.add(/*TODO: ISO 8601*/val.toString());
      }
    }
  }
}
