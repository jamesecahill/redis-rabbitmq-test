package test;

import java.io.IOException;
import java.util.Random;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;


public class Worker implements Runnable, Stoppable {
    private Connection connection;
    private Channel channel;
    boolean hasFailure;

    @Override
    public void run() {
        String message;
        int waitMs;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(Main.QUEUE, true, false, false, null);
            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(Main.QUEUE, false, consumer);

            while(!Thread.interrupted()) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                message = new String(delivery.getBody());
                //wait 0 to 200ms
                waitMs = new Random().nextInt(20) * 10;
                System.out.println(String.format("RECV: \"%s\" on Thread: %s (work will take %dms)", message, Thread.currentThread().getName(), waitMs));
                //simulate failure 5% of the time
                if (Math.random() > 0.05) {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else {
                    System.out.println(String.format("NACK: \"%s\" on Thread: %s", message, Thread.currentThread().getName()));
                    hasFailure = true;
                    break;
                }
                Thread.sleep(waitMs);
            }
        } catch (ShutdownSignalException e) {
            //ignore
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
                if (connection.isOpen()) {
                    connection.close();
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
