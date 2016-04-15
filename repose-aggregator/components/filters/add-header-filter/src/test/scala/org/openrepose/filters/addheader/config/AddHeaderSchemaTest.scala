/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.filters.addheader.config

import org.junit.runner.RunWith
import org.openrepose.commons.test.ConfigValidator
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSpec, Matchers}
import org.xml.sax.SAXParseException

@RunWith(classOf[JUnitRunner])
class AddHeaderSchemaTest extends FunSpec with Matchers {
  val validator = ConfigValidator("/META-INF/schema/config/add-header.xsd")

  describe("schema validation") {
    it("should successfully validate the sample config") {
      validator.validateConfigFile("/META-INF/schema/examples/add-header.cfg.xml")
    }

    it("should successfully validate config if at a request header is defined") {
      val config =
        """<add-headers xmlns="http://docs.openrepose.org/repose/add-header/v1.0">
          |    <request>
          |        <header name="foo">bar</header>
          |    </request>
          |</add-headers>""".stripMargin
      validator.validateConfigString(config)
    }

    it("should successfully validate config if at a response header is defined") {
      val config =
        """<add-headers xmlns="http://docs.openrepose.org/repose/add-header/v1.0">
          |    <response>
          |        <header name="foo">bar</header>
          |    </response>
          |</add-headers>""".stripMargin
      validator.validateConfigString(config)
    }

    it("should reject config if no headers are defined") {
      val config =
        """<add-headers xmlns="http://docs.openrepose.org/repose/add-header/v1.0"/>""".stripMargin
      intercept[SAXParseException] {
        validator.validateConfigString(config)
      }.getLocalizedMessage should include("At least one header must be defined.")
    }
  }
}
