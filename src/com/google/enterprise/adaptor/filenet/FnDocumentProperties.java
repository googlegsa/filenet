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

import com.filenet.api.collection.BooleanList;
import com.filenet.api.collection.DateTimeList;
import com.filenet.api.collection.Float64List;
import com.filenet.api.collection.IdList;
import com.filenet.api.collection.Integer32List;
import com.filenet.api.collection.StringList;
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
import java.util.Iterator;
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
      getStringValue(name, prop, list);
    } else if (prop instanceof PropertyStringList) {
      getStringListValue(name, prop, list);
    } else if (prop instanceof PropertyBoolean) {
      getBooleanValue(name, prop, list);
    } else if (prop instanceof PropertyBooleanList) {
      getBooleanListValue(name, prop, list);
    } else if (prop instanceof PropertyDateTime) {
      getDateValue(name, prop, list);
    } else if (prop instanceof PropertyDateTimeList) {
      getDateListValue(name, prop, list);
    } else if (prop instanceof PropertyFloat64) {
      getDoubleValue(name, prop, list);
    } else if (prop instanceof PropertyFloat64List) {
      getDoubleListValue(name, prop, list);
    } else if (prop instanceof PropertyInteger32) {
      getLongValue(name, prop, list);
    } else if (prop instanceof PropertyInteger32List) {
      getLongListValue(name, prop, list);
    } else if (prop instanceof PropertyId) {
      getGuidValue(name, prop, list);
    } else if (prop instanceof PropertyIdList) {
      getGuidListValue(name, prop, list);
    } else {
      logger.log(Level.FINEST, "Property type is not supported: {0}",
          prop.getClass().getName());
    }
  }

  private void getStringValue(String propertyName, Property prop,
      List<String> valuesList) {
    String val = prop.getStringValue();
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getStringListValue(String propertyName, Property prop,
      List<String> valuesList) {
    StringList slist = prop.getStringListValue();
    Iterator<?> iter = slist.iterator();
    while (iter.hasNext()) {
      String val = (String) iter.next();
      if (val != null) {
        valuesList.add(val.toString());
      }
    }
  }

  private void getGuidValue(String propertyName, Property prop,
      List<String> valuesList) {
    Id val = prop.getIdValue();
    if (val != null) {
      String id = val.toString();
      valuesList.add(id.substring(1, id.length() - 1));
    }
  }

  private void getGuidListValue(String propertyName, Property prop,
      List<String> valuesList) {
    IdList idList = prop.getIdListValue();
    Iterator<?> iter = idList.iterator();
    while (iter.hasNext()) {
      Id val = (Id) iter.next();
      if (val != null) {
        String id = val.toString();
        valuesList.add(id.substring(1, id.length() - 1));
      }
    }
  }

  private void getLongValue(String propertyName, Property prop,
      List<String> valuesList) {
    Integer val = prop.getInteger32Value();
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getLongListValue(String propertyName, Property prop,
      List<String> valuesList) {
    Integer32List int32List = prop.getInteger32ListValue();
    Iterator<?> iter = int32List.iterator();
    while (iter.hasNext()) {
      Integer val = (Integer) iter.next();
      if (val != null) {
        valuesList.add(val.toString());
      }
    }
  }

  private void getDoubleValue(String propertyName, Property prop,
      List<String> valuesList) {
    Double val = prop.getFloat64Value();
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getDoubleListValue(String propertyName, Property prop,
      List<String> valuesList) {
    Float64List float64List = prop.getFloat64ListValue();
    Iterator<?> iter = float64List.iterator();
    while (iter.hasNext()) {
      Double val = (Double) iter.next();
      if (val != null) {
        valuesList.add(val.toString());
      }
    }
  }

  private void getDateValue(String propertyName, Property prop,
      List<String> valuesList) {
    Date val = prop.getDateTimeValue();
    if (val != null) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(val);
      valuesList.add(/*TODO: ISO 8601*/val.toString());
    }
  }

  private void getDateListValue(String propertyName, Property prop,
      List<String> valuesList) {
    DateTimeList dtList = prop.getDateTimeListValue();
    Iterator<?> iter = dtList.iterator();
    while (iter.hasNext()) {
      Date val = (Date) iter.next();
      if (val != null) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(val);
        valuesList.add(/*TODO: ISO 8601*/val.toString());
      }
    }
  }

  private void getBooleanValue(String propertyName, Property prop,
      List<String> valuesList) {
    Boolean val = prop.getBooleanValue();
    if (val != null) {
      valuesList.add(val.toString());
    }
  }

  private void getBooleanListValue(String propertyName, Property prop,
      List<String> valuesList) {
    BooleanList booleanList = prop.getBooleanListValue();
    Iterator<?> iter = booleanList.iterator();
    while (iter.hasNext()) {
      Boolean val = (Boolean) iter.next();
      if (val != null) {
        valuesList.add(val.toString());
      }
    }
  }
}
