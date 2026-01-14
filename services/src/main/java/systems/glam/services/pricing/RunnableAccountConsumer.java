package systems.glam.services.pricing;

public interface RunnableAccountConsumer extends AccountConsumer, Runnable {


  boolean unsupported();
}
