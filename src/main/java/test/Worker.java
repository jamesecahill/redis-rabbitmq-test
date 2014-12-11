package test;

import java.io.IOException;
import java.util.Random;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

public class Worker implements Runnable, Stoppable {
    private Connection connection;
    private Channel channel;
    boolean hasFailure;

    public Worker(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        String message;
        int waitMs;

        try {
            channel = connection.createChannel();
            channel.queueDeclare(Main.QUEUE, true, false, false, null);
            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(Main.QUEUE, false, consumer);

            while (!Thread.interrupted()) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                message = new String(delivery.getBody());
                waitMs = new Random().nextInt(Main.MAX_WORKER_WAIT - Main.MIN_WORKER_WAIT) + Main.MIN_WORKER_WAIT;
                System.out.println(String.format("RECV: \"%s\" on Thread: %s (work will take %dms)", message, Thread.currentThread().getName(), waitMs));
                // simulate failure 5% of the time
                if (Math.random() > Main.WORKER_FAILURE_PERCENT) {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else {
                    System.out.println(String.format("NACK: \"%s\" on Thread: %s", message, Thread.currentThread().getName()));
                    hasFailure = true;
                    break;
                }
                Thread.sleep(waitMs);
            }
        } catch (ShutdownSignalException e) {
            // ignore
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean hasFailure() {
        return hasFailure;
    }
}
