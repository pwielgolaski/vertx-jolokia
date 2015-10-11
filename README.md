# vertx-jolokia

Simple integrattion between vertx & jolokia.

Instead of using jolokia agent we can use netty server.

Sample of usage:
```
public class SampleVerticle extends AbstractVerticle {

  public static final int PORT = 8080;

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    router.route("/jolokia/*").handler(JolokiaHandler.create());

    vertx.createHttpServer().requestHandler(router::accept).listen(PORT);
  }
}
```
