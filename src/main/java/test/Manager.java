package test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.core.RLock;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;

public class Manager implements Runnable, Stoppable {
    private Connection connection;
    private Channel channel;
    private AtomicBoolean isSending = new AtomicBoolean(false);

    public Manager(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        String message;
        try {
            channel = connection.createChannel();
            channel.queueDeclare(Main.QUEUE, true, false, false, null);

            while (!Thread.interrupted()) {
                if (isSending.get()) {
                    long messageNumber;
                    // fail to incrememnt some times to simulate two managers
                    // sending the same message
                    // which will cause us to rely on locks
                    if (Math.random() > Main.MANAGER_FAILURE_PERCENT) {
                        messageNumber = Main.messageCounts.getAndIncrement();
                    } else {
                        messageNumber = Main.messageCounts.get();
                    }
                    message = String.format("Message #%d", messageNumber);
                    RLock lock = Main.redisson.getLock(String.format("msg%d", messageNumber));
                    // get a lock for this publish, or move on
                    if (lock.tryLock()) {
                        String completionKey = String.format("msg%dsent", messageNumber);
                        // only publish the message if it hasn't already been
                        // published
                        if (!Main.redisson.getBucket(completionKey).exists()) {
                            channel.basicPublish("", Main.QUEUE, null, message.getBytes());
                            // publish that this message has been sent so nothing will send it again for 30 seconds.
                            Main.redisson.getBucket(completionKey).setAsync(Boolean.TRUE, 30, TimeUnit.SECONDS);
                            System.out.println(String.format("SENT: \"%s\" from Thread: %s", message, Thread.currentThread().getName()));
                        }
                        lock.unlock();
                    } else {
                        // another manager has this message locked
                        System.out.println(String.format("Skipping message %d due to failed lock", messageNumber));
                    }
                }
                Thread.sleep(Main.MANAGER_SEND_INTERVAL);
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

    public void on() {
        isSending.set(true);
    }

    public void off() {
        isSending.set(false);
    }
}
