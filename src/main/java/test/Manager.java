package test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;

public class Manager implements Runnable, Stoppable {
    private Connection connection;
    private Channel channel;
    private AtomicBoolean isSending = new AtomicBoolean(false);

    @Override
    public void run() {
        String message;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(Main.QUEUE, true, false, false, null);

            while(!Thread.interrupted()) {
                if (isSending.get()) {
                    message = String.format("Message #%d", Main.messageCounts.getAndIncrement());
                    channel.basicPublish("", Main.QUEUE, null, message.getBytes());
                    System.out.println(String.format("SENT: \"%s\" from Thread: %s", message, Thread.currentThread().getName()));
                }
                Thread.sleep(10);
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

    public void on() {
        isSending.set(true);
    }

    public void off() {
        isSending.set(false);
    }
}
