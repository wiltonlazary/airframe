package wvlet.airframe.http.okhttp

import wvlet.airframe.control.Control.withResource
import wvlet.airframe.http.HttpMessage.{Request, Response}
import wvlet.airframe.http.netty.{Netty, NettyServer}
import wvlet.airframe.http.{HttpClientException, HttpClientMaxRetryException, HttpStatus, Router}
import wvlet.airspec.AirSpec
import wvlet.log.LogSupport

import java.time.Duration

case class User(id: Int, name: String, requestId: String) {
  def withRequestId(newRequestId: String): User = User(id, name, newRequestId)
}

case class UserRequest(id: Int, name: String)
case class DeleteRequestBody(force: Boolean)

trait NettyTestApi extends LogSupport {
  import wvlet.airframe.http.{Endpoint, HttpMethod}

  @Endpoint(method = HttpMethod.GET, path = "/")
  def info: String = {
    "Ok"
  }

  private def getRequestId(request: Request): String = {
    request.header.getOrElse("X-Request-Id", "N/A")
  }

  @Endpoint(method = HttpMethod.GET, path = "/user/:id")
  def get(id: Int, request: Request): User = {
    User(id, "leo", getRequestId(request))
  }

  @Endpoint(method = HttpMethod.GET, path = "/user/info")
  def getResource(id: Int, name: String, request: Request): User = {
    User(id, name, getRequestId(request))
  }

  @Endpoint(method = HttpMethod.GET, path = "/user/info2")
  def getResource(query: UserRequest, request: Request): User = {
    User(query.id, query.name, getRequestId(request))
  }

  @Endpoint(method = HttpMethod.GET, path = "/user")
  def list(request: Request): Seq[User] = {
    Seq(User(1, "leo", getRequestId(request)))
  }

  @Endpoint(method = HttpMethod.POST, path = "/user")
  def create(newUser: User, request: Request): User = {
    newUser.withRequestId(getRequestId(request))
  }

  @Endpoint(method = HttpMethod.DELETE, path = "/user/:id")
  def delete(id: Int, request: Request): User = {
    User(id, "xxx", getRequestId(request))
  }

  @Endpoint(method = HttpMethod.PUT, path = "/user")
  def put(updatedUser: User, request: Request): User = {
    updatedUser.withRequestId(getRequestId(request))
  }

  @Endpoint(method = HttpMethod.PATCH, path = "/user")
  def patch(updatedUser: User, request: Request): User = {
    updatedUser.withRequestId(getRequestId(request))
  }

  @Endpoint(method = HttpMethod.GET, path = "/busy")
  def busy: Response = {
    trace("called busy method")
    Response(HttpStatus.InternalServerError_500)
  }

  @Endpoint(method = HttpMethod.GET, path = "/forbidden")
  def forbidden: Response = {
    Response(HttpStatus.Forbidden_403)
  }

  @Endpoint(method = HttpMethod.GET, path = "/readtimeout")
  def readTimeout: String = {
    Thread.sleep(3000)
    "called readTimeout method"
  }

  @Endpoint(method = HttpMethod.GET, path = "/response")
  def rawResponse: Response = {
    val r = Response(HttpStatus.Ok_200)
    r.withContent("raw response")
  }
}

class OkHttpClientTest extends AirSpec {
  private val r = Router.add[NettyTestApi]

  override protected def design = {
    Netty.server.withRouter(r).designWithSyncClient
  }

  test("create client") { (server: NettyServer) =>
    def addRequestId(request: okhttp3.Request.Builder): okhttp3.Request.Builder = {
      request.addHeader("X-Request-Id", "10")
    }

    withResource(OkHttpClient.newClient(server.localAddress)) { client =>
      // Sending an implementation specific Request type
      val ret = client.send(new okhttp3.Request.Builder().url(s"http://${server.localAddress}/")).contentString
      ret shouldBe "Ok"

      // Using HTTP request wrappers
      client.get[User]("/user/1") shouldBe User(1, "leo", "N/A")
      client.getResource[UserRequest, User]("/user/info", UserRequest(2, "kai")) shouldBe User(2, "kai", "N/A")
      client.getResource[UserRequest, User]("/user/info2", UserRequest(2, "kai")) shouldBe User(2, "kai", "N/A")
      client.getOps[UserRequest, User]("/user/info2", UserRequest(2, "kai")) shouldBe User(2, "kai", "N/A")
      client.list[Seq[User]]("/user") shouldBe Seq(User(1, "leo", "N/A"))

      client.post[User]("/user", User(2, "yui", "N/A")) shouldBe User(2, "yui", "N/A")
      client.postOps[User, User]("/user", User(2, "yui", "N/A")) shouldBe User(2, "yui", "N/A")

      client.put[User]("/user", User(10, "aina", "N/A")) shouldBe User(10, "aina", "N/A")
      client.putOps[User, User]("/user", User(10, "aina", "N/A")) shouldBe User(10, "aina", "N/A")

      client.patch[User]("/user", User(20, "joy", "N/A")) shouldBe User(20, "joy", "N/A")
      client.patchOps[User, User]("/user", User(20, "joy", "N/A")) shouldBe User(20, "joy", "N/A")

      client.delete[User]("/user/1") shouldBe User(1, "xxx", "N/A")
      client.deleteOps[DeleteRequestBody, User]("/user/1", DeleteRequestBody(true)) shouldBe User(1, "xxx", "N/A")

      // Get a response as is
      client.get[okhttp3.Response]("/response").contentString shouldBe "raw response"

      // Using a custom HTTP header
      client.get[User]("/user/1", addRequestId) shouldBe User(1, "leo", "10")
      client.getResource[UserRequest, User]("/user/info", UserRequest(2, "kai"), addRequestId) shouldBe User(
        2,
        "kai",
        "10"
      )
      client.getResource[UserRequest, User]("/user/info2", UserRequest(2, "kai"), addRequestId) shouldBe User(
        2,
        "kai",
        "10"
      )

      client.list[Seq[User]]("/user", addRequestId) shouldBe Seq(User(1, "leo", "10"))

      client.post[User]("/user", User(2, "yui", "N/A"), addRequestId) shouldBe User(2, "yui", "10")
      client.postOps[User, User]("/user", User(2, "yui", "N/A"), addRequestId) shouldBe User(2, "yui", "10")
      client
        .postRaw[User](
          "/user",
          User(2, "yui", "N/A"),
          addRequestId
        ).contentString shouldBe """{"id":2,"name":"yui","requestId":"10"}"""

      client.put[User]("/user", User(10, "aina", "N/A"), addRequestId) shouldBe User(10, "aina", "10")
      client.putOps[User, User]("/user", User(10, "aina", "N/A"), addRequestId) shouldBe User(10, "aina", "10")
      client
        .putRaw[User](
          "/user",
          User(10, "aina", "N/A"),
          addRequestId
        ).contentString shouldBe """{"id":10,"name":"aina","requestId":"10"}"""

      client.patch[User]("/user", User(20, "joy", "N/A"), addRequestId) shouldBe User(20, "joy", "10")
      client.patchOps[User, User]("/user", User(20, "joy", "N/A"), addRequestId) shouldBe User(20, "joy", "10")
      client
        .patchRaw[User](
          "/user",
          User(20, "joy", "N/A"),
          addRequestId
        ).contentString shouldBe """{"id":20,"name":"joy","requestId":"10"}"""

      client.delete[User]("/user/1", addRequestId) shouldBe User(1, "xxx", "10")
      client.deleteOps[DeleteRequestBody, User]("/user/1", DeleteRequestBody(true), addRequestId) shouldBe User(
        1,
        "xxx",
        "10"
      )
      client.deleteRaw("/user/1", addRequestId).contentString shouldBe """{"id":1,"name":"xxx","requestId":"10"}"""
    }
  }

  test("fail request") { (server: NettyServer) =>
    withResource(
      OkHttpClient.newClient(
        // Test for the full URI
        s"http://${server.localAddress}",
        OkHttpClientConfig().withMaxRetry(3).withBackOff(initialIntervalMillis = 1)
      )
    ) { client =>
      warn("Starting http client failure tests")

      {
        // Test max retry failure
        val ex = intercept[HttpClientMaxRetryException] {
          client.get[String]("/busy")
        }
        warn(ex.getMessage)
        ex.retryContext.retryCount shouldBe 3
        ex.retryContext.maxRetry shouldBe 3
        val cause = ex.retryContext.lastError.asInstanceOf[HttpClientException]
        cause.status shouldBe HttpStatus.InternalServerError_500
      }

      {
        // Non retryable response
        val cause = intercept[HttpClientException] {
          client.get[String]("/forbidden")
        }
        warn(cause.getMessage)
        cause.status shouldBe HttpStatus.Forbidden_403
      }
    }
  }

  test("read timeout") { (server: NettyServer) =>
    withResource(
      OkHttpClient.newClient(
        s"http://${server.localAddress}",
        OkHttpClientConfig(timeout = Duration.ofMillis(10)).withMaxRetry(1)
      )
    ) { client =>
      warn("Starting a read timeout test")

      val ex = intercept[HttpClientMaxRetryException] {
        // sleeps for 3 seconds, which means a timeout happens
        client.get[String]("/readtimeout")
      }
      warn(ex.getMessage)
      ex.retryContext.retryCount shouldBe 1
      ex.retryContext.maxRetry shouldBe 1
      ex.retryContext.lastError.getClass shouldBe classOf[java.net.SocketTimeoutException]
    }
  }

  test("support https request") {
    withResource(OkHttpClient.newClient("https://wvlet.org")) { client =>
      val page = client.get[String]("/airframe/")
      trace(page)
      page.contains("<html") shouldBe true
    }
  }

}
