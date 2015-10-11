import com.simplesw.vertx.jolokia.JolokiaHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class SampleVerticle extends AbstractVerticle {

  public static final int PORT = 8080;
  public static final Logger log = LoggerFactory.getLogger(SampleVerticle.class);

  public static void main(String[] args) {
    Launcher.main(new String[]{"run", SampleVerticle.class.getName()});
  }

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    router.route("/jolokia/*").handler(JolokiaHandler.create());

    vertx.createHttpServer().requestHandler(router::accept).listen(PORT);

    HttpClient httpClient = vertx.createHttpClient();
    vertx.setPeriodic(1000, t -> {
      httpClient.getNow(PORT, "localhost", "/jolokia/read/java.lang:type=Memory/HeapMemoryUsage/used", res -> {
        res.bodyHandler(totalBuffer -> {
          JsonObject json = new JsonObject(totalBuffer.toString());
          log.info("HeapMemoryUsage/used {0}", json.getValue("value"));
        });
      });
    });
  }
}
