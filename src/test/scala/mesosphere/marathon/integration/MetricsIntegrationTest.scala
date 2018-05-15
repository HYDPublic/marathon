package mesosphere.marathon
package integration

import mesosphere.AkkaIntegrationTest
import mesosphere.marathon.integration.setup.EmbeddedMarathonTest
import mesosphere.marathon.raml.GroupUpdate
import play.api.libs.json.{JsObject, Json}

@IntegrationTest
class MetricsIntegrationTest extends AkkaIntegrationTest with EmbeddedMarathonTest {

  "Marathon Metrics" should {
    "correctly count outgoing HTTP bytes" in {

      When("The metrics endpoint is queried")
      val result = marathon.metrics()

      Then("The system responds as expected")
      result should be(OK)
      result.entityJson.as[JsObject].keys should contain("counters")
      result.entityJson("counters").as[JsObject].keys should contain("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")

      And("The `outputBytes` is increased as expected")
      val currentCounter = result.entityJson("counters")("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")("count").as[Int]
      val newResult = marathon.metrics()
      val newCounter = newResult.entityJson("counters")("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")("count").as[Int]
      newCounter should be(currentCounter + newResult.entityString.length)

    }

    "correctly count incoming HTTP bytes" in {

      When("The metrics endpoint is queried")
      val result = marathon.metrics()

      Then("The system responds as expected")
      result should be(OK)
      result.entityJson.as[JsObject].keys should contain("counters")
      result.entityJson("counters").as[JsObject].keys should contain("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")

      And("The `inputBytes` is increased as expected")
      val currentCounter = result.entityJson("counters")("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")("count").as[Int]
      val requestObj = GroupUpdate(id = Some("/empty"))
      val requestJson = Json.toJson(requestObj).toString()
      marathon.createGroup(requestObj)

      val newResult = marathon.metrics()
      val newCounter = newResult.entityJson("counters")("service.mesosphere.marathon.api.HTTPMetricsFilter.outputBytes")("count").as[Int]
      newCounter should be(currentCounter + requestJson.length)

    }
  }

}
