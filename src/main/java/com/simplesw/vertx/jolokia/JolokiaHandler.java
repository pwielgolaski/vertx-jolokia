package com.simplesw.vertx.jolokia;

import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.Arguments;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
import org.jolokia.backend.BackendManager;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.http.HttpRequestHandler;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.DenyAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.restrictor.RestrictorFactory;
import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import javax.management.RuntimeMBeanException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class JolokiaHandler implements Handler<RoutingContext> {

  public static final String STATUS = "status";
  private final HttpRequestHandler requestHandler;
  private final LogHandler log;
  private final String contentType = "application/json";

  public static JolokiaHandler create() {
    return create(Collections.<String, String>emptyMap());
  }

  public static JolokiaHandler create(Map<String, String> config) {
    return create(config, null);
  }

  public static JolokiaHandler create(Map<String, String> config, Restrictor restrictor) {
    return new JolokiaHandler(config, restrictor);
  }

  protected JolokiaHandler(Map<String, String> configParameters, Restrictor restrictor) {
    log = new JolokiaLogHandler(LoggerFactory.getLogger(JolokiaHandler.class));
    Configuration config = initConfig(configParameters);
    if (restrictor == null) {
      restrictor = createRestrictor(NetworkUtil.replaceExpression(config.get(ConfigKey.POLICY_LOCATION)));
    }
    log.info("Using restrictor " + restrictor);
    BackendManager backendManager = new BackendManager(config, log, restrictor);
    requestHandler = new HttpRequestHandler(config, backendManager, log);
  }

  protected Configuration initConfig(Map<String, String> params) {
    Configuration config = new Configuration(ConfigKey.AGENT_ID, NetworkUtil.getAgentId(hashCode(), "vertx"));
    config.updateGlobalConfiguration(params);
    return config;
  }

  protected Restrictor createRestrictor(String pLocation) {
    try {
      Restrictor newRestrictor = RestrictorFactory.lookupPolicyRestrictor(pLocation);
      if (newRestrictor != null) {
        log.info("Using access restrictor " + pLocation);
        return newRestrictor;
      } else {
        log.info("No access restrictor found at " + pLocation + ", access to all MBeans is allowed");
        return new AllowAllRestrictor();
      }
    } catch (IOException e) {
      log.error("Error while accessing access restrictor at " + pLocation +
        ". Denying all access to MBeans for security reasons. Exception: " + e, e);
      return new DenyAllRestrictor();
    }
  }

  @Override
  public void handle(RoutingContext context) {
    HttpServerRequest req = context.request();
    String remainingPath = Utils.pathOffset(req.path(), context);

    if (req.method() != HttpMethod.GET && req.method() != HttpMethod.POST) {
      context.next();
    }

    JSONAware json = null;
    try {
      // Check access policy
      requestHandler.checkAccess(req.remoteAddress().host(), req.remoteAddress().host(), getOriginOrReferer(req));
      if (req.method() == HttpMethod.GET) {
        json = requestHandler.handleGetRequest(req.uri(), remainingPath, getParams(req.params()));
      } else {
        Arguments.require(context.getBody() != null, "Missing body, make sure that BodyHandler is used before");
        // TODO how to get Stream ?
        InputStream inputStream = new ByteBufInputStream(context.getBody().getByteBuf());
        json = requestHandler.handlePostRequest(req.uri(), inputStream, StandardCharsets.UTF_8.name(), getParams(req.params()));
      }
    } catch (Throwable exp) {
      json = requestHandler.handleThrowable(exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
    } finally {
      if (json == null)
        json = requestHandler.handleThrowable(new Exception("Internal error while handling an exception"));

      context.response()
        .setStatusCode(getStatusCode(json))
        .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
        .end(json.toJSONString());
    }
  }

  protected int getStatusCode(JSONAware json) {
    if (json instanceof JSONObject && ((JSONObject) json).get(STATUS) instanceof Integer) {
      return (Integer) ((JSONObject) json).get(STATUS);
    }
    return 200;
  }

  protected Map<String, String[]> getParams(MultiMap params) {
    Map<String, String[]> response = new HashMap<>();
    for (String name : params.names()) {
      response.put(name, params.getAll(name).toArray(new String[0]));
    }
    return response;
  }

  protected String getOriginOrReferer(HttpServerRequest req) {
    String origin = req.getHeader(HttpHeaders.ORIGIN);
    if (origin == null) {
      origin = req.getHeader(HttpHeaders.REFERER);
    }
    return origin != null ? origin.replaceAll("[\\n\\r]*", "") : null;
  }
}