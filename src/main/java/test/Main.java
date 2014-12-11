package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.Redisson;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Main {
    public static final String QUEUE = "work";

    // counts
    private static final int WORKER_COUNT = 20;
    private static final int MANAGER_COUNT = 2;

    // intervals
    public static final long GROOM_INTERVAL = 500;
    public static final int MAX_WORKER_WAIT = 50;
    public static final int MIN_WORKER_WAIT = 5;
    public static final double WORKER_FAILURE_PERCENT = 0.05;
    public static final long MANAGER_SEND_INTERVAL = 20;
    public static final double MANAGER_FAILURE_PERCENT = 0.05;

    public static AtomicLong messageCounts = new AtomicLong(1);
    // this is thread-safe
    public static Redisson redisson;

    private Main() {
        throw new RuntimeException();
    }

    public static void main(String... args) throws IOException {
        redisson = Redisson.create();

        ExecutorService svc = Executors.newFixedThreadPool(WORKER_COUNT + MANAGER_COUNT + 1);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();

        // start managers
        Manager[] mgrs = new Manager[MANAGER_COUNT];
        for (int i = 0; i < MANAGER_COUNT; i++) {
            mgrs[i] = new Manager(connection);
            svc.execute(mgrs[i]);
        }

        // start a couple workers
        Worker[] workers = new Worker[WORKER_COUNT];
        for (int i = 0; i < WORKER_COUNT; i++) {
            workers[i] = new Worker(connection);
            svc.execute(workers[i]);
        }

        // start a groomer
        Groomer groomer = new Groomer(svc, workers, connection);
        svc.execute(groomer);

        System.out.println("x to exit, o to start, f to stop, g to groom");

        // wait for exit
        while (true) {
            String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (input.equalsIgnoreCase("x")) {
                break;
            } else if (input.equalsIgnoreCase("o")) {
                for (Manager m : mgrs) {
                    m.on();
                }
            } else if (input.equalsIgnoreCase("f")) {
                for (Manager m : mgrs) {
                    m.off();
                }
            } else if (input.equalsIgnoreCase("g")) {
                groomer.groom();
            }
        }

        // shutdown manager and workers
        for (Worker w : workers) {
            w.stop();
        }
        for (Manager m : mgrs) {
            m.stop();
        }

        // shutdown groomer
        groomer.stop();

        // shutdown redisson
        redisson.shutdown();

        // peace out
        System.exit(0);
    }
}
