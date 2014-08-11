package com.rackspace.papi.components.keystone.v3

import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.MediaType

import com.rackspace.papi.commons.util.http.HttpStatusCode
import com.rackspace.papi.commons.util.servlet.http.ReadableHttpServletResponse
import com.rackspace.papi.components.keystone.v3.config.KeystoneV3Config
import com.rackspace.papi.components.keystone.v3.json.spray.IdentityJsonProtocol._
import com.rackspace.papi.components.keystone.v3.objects._
import com.rackspace.papi.filter.logic.common.AbstractFilterLogicHandler
import com.rackspace.papi.filter.logic.impl.FilterDirectorImpl
import com.rackspace.papi.filter.logic.{FilterAction, FilterDirector}
import com.rackspace.papi.service.datastore.DatastoreService
import com.rackspace.papi.service.httpclient.HttpClientService
import com.rackspace.papi.service.serviceclient.akka.AkkaServiceClient
import org.apache.http.Header
import org.slf4j.LoggerFactory
import spray.json._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class KeystoneV3Handler(keystoneConfig: KeystoneV3Config, akkaServiceClient: AkkaServiceClient, datastoreService: DatastoreService, connectionPoolService: HttpClientService[_, _])
        extends AbstractFilterLogicHandler {

    private final val LOG = LoggerFactory.getLogger(classOf[KeystoneV3Handler])
    private final val ADMIN_TOKEN_KEY = "ADMIN_TOKEN"
    private final val TOKEN_KEY_PREFIX = "TOKEN:"
    private final val TOKEN_ENDPOINT = "/v3/auth/tokens"
    private final val X_AUTH_TOKEN = "X-Auth-Token"
    private final val X_SUBJECT_TOKEN = "X-Subject-Token"

    // TODO stop being lazy!
    private lazy val keystoneServiceUri = keystoneConfig.getKeystoneService.getUri

    override def handleRequest(request: HttpServletRequest, response: ReadableHttpServletResponse): FilterDirector = {
        if (isUriWhitelisted(request.getRequestURI, keystoneConfig.getWhiteList.getUriPattern.asScala.toList)) {
            LOG.debug("Request URI matches a configured whitelist pattern! Allowing request to pass through.")
            val filterDirector: FilterDirector = new FilterDirectorImpl()
            filterDirector.setFilterAction(FilterAction.PASS)
            filterDirector
        } else {
            authenticate(request)
        }
    }

    private def isUriWhitelisted(requestUri: String, whiteList: List[String]) =
        whiteList.filter(requestUri.matches).nonEmpty

    private def authenticate(request: HttpServletRequest) = {
        val filterDirector: FilterDirector = new FilterDirectorImpl()
        filterDirector.setFilterAction(FilterAction.RETURN)
        filterDirector.setResponseStatus(HttpStatusCode.UNAUTHORIZED)
        val subjectToken = request.getHeader(X_SUBJECT_TOKEN)

        if (subjectToken == null) {
            filterDirector.setResponseStatus(HttpStatusCode.UNAUTHORIZED)
            filterDirector.setFilterAction(FilterAction.RETURN)
        } else {
            val tokenObject = validateSubjectToken(subjectToken)
            // TODO
        }

        filterDirector
    }

    private def validateSubjectToken(subjectToken: String) = {
        // TODO: Check cache
        //        datastoreService.getDefaultDatastore

        // TODO: Check/get admin token
        val adminToken = fetchAdminToken() match {
            case Success(v) => v
            case Failure(e) => ""// TODO: Should a Runtime exception be thrown instead?
        }

        val headerMap = Map(X_AUTH_TOKEN -> adminToken, X_SUBJECT_TOKEN -> subjectToken)
        val validateTokenResponse = akkaServiceClient.get(TOKEN_KEY_PREFIX + subjectToken, keystoneServiceUri + TOKEN_ENDPOINT, headerMap.asJava)

    }

    private def fetchAdminToken(): Try[String] = {
        // TODO: Check cache (datastore, can't use a local variable if we are following the standard filter pattern
        //       where a handler is created for each response)
        //        datastoreService.getDefaultDatastore.get(CACHE_KEY)

        val requestJson = createAdminAuthRequest(
            keystoneConfig.getKeystoneService.getUsername,
            keystoneConfig.getKeystoneService.getPassword,
            keystoneConfig.getKeystoneService.getDomainId
        )
        val generateAuthTokenResponse = akkaServiceClient.post(ADMIN_TOKEN_KEY, keystoneServiceUri + TOKEN_ENDPOINT, Map[String, String]().asJava, requestJson, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE)
        HttpStatusCode.fromInt(generateAuthTokenResponse.getStatusCode) match {
            case HttpStatusCode.OK =>
                //                val expirationTtl = // TODO: Parse the expires-at field from the response
                // TODO: datastoreService.getDefaultDatastore.put(CACHE_KEY, "", expirationTime - System.currentTimeMillis())
                Success(generateAuthTokenResponse.getHeaders.filter((header: Header) => header.getName.equalsIgnoreCase(X_SUBJECT_TOKEN)).head.getValue)
            case _ =>
                LOG.error("Unable to get admin token. Please verify your admin credentials. Response Code: " + generateAuthTokenResponse.getStatusCode)
                Failure(new Exception()) // TODO: Return a Failure(some_exception)
        }
    }

    private def createAdminAuthRequest(user: String, password: String, domainId: String) = {
        var domainType: Option[DomainType] = None
        if (domainId != null) domainType = Some(DomainType(id = Some(domainId)))

        AuthRequest(
            AuthIdentityRequest(
                List("password"),
                Some(PasswordCredentials(
                    UserNamePasswordRequest(
                        domain = domainType,
                        name = Some(keystoneConfig.getKeystoneService.getUsername),
                        password = keystoneConfig.getKeystoneService.getPassword
                    )
                ))
            )
        ).toJson.compactPrint
    }
}
