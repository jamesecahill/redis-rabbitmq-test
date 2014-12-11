package test;

import java.util.concurrent.ExecutorService;

import com.rabbitmq.client.Connection;

public class Groomer implements Runnable, Stoppable {
    private ExecutorService svc;
    private Worker[] workers;
    private Connection connection;

    public Groomer(ExecutorService svc, Worker[] workers, Connection connection) {
        this.svc = svc;
        this.workers = workers;
        this.connection = connection;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                groom();
                Thread.sleep(Main.GROOM_INTERVAL);
            } catch (InterruptedException e) {
            }
        }
    }

    public void groom() {
        for (int i = 0; i < workers.length; i++) {
            if (workers[i].hasFailure()) {
                System.out.println(String.format("GROOM: Removing failed worker #%d", i + 1));
                synchronized (workers) {
                    workers[i] = new Worker(connection);
                    svc.execute(workers[i]);
                }
            }
        }
    }

}
