package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import redis.clients.jedis.Jedis;

public class Main {
    public static final String QUEUE = "work";
    private static final int WORKER_COUNT = 20;
    private static final int MANAGER_COUNT = 2;
    public static AtomicLong messageCounts = new AtomicLong(1);

    private Main() {
        throw new RuntimeException();
    }

    public static void main(String... args) throws IOException {
        Jedis jedis = new Jedis("localhost");
        jedis.setex("ts", 1, String.valueOf(System.currentTimeMillis()));
        jedis.close();

        ExecutorService svc = Executors.newFixedThreadPool(WORKER_COUNT + MANAGER_COUNT + 1);

        // start managers
        Manager[] mgrs = new Manager[MANAGER_COUNT];
        for (int i=0; i<MANAGER_COUNT; i++) {
            mgrs[i] = new Manager();
            svc.execute(mgrs[i]);
        }

        // start a couple workers
        Worker[] workers = new Worker[WORKER_COUNT];
        for (int i=0; i<WORKER_COUNT; i++) {
            workers[i] = new Worker();
            svc.execute(workers[i]);
        }

        // start a groomer
        Groomer groomer = new Groomer(svc, workers);
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

        //shutdown groomer
        groomer.stop();

        //peace out
        System.exit(0);
    }
}
