// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.filenet;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

/** For getting FileNet repository content into a Google Search Appliance. */
public class FileNetAdaptor extends AbstractAdaptor {

  public static void main(String[] args) {
    AbstractAdaptor.main(new FileNetAdaptor(), args);
  }

  @Override
  public void initConfig(Config config) {
  }

  @Override
  public void init(AdaptorContext context) throws Exception {
  }

  @Override
  public void getDocIds(DocIdPusher pusher) {
  }

  @Override
  public void getDocContent(Request req, Response resp) {
  }
}
