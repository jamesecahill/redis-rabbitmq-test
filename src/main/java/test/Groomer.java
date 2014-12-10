package test;

import java.util.concurrent.ExecutorService;

public class Groomer implements Runnable, Stoppable {
    private ExecutorService svc;
    private Worker[] workers;

    public Groomer(ExecutorService svc, Worker[] workers) {
        this.svc = svc;
        this.workers = workers;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                groom();
                Thread.sleep(1000);
            } catch (InterruptedException e) { }
        }
    }

    public void groom() {
        for (int i=0; i<workers.length; i++) {
            if (workers[i].hasFailure()) {
                System.out.println(String.format("GROOM: Removing failed worker #%d", i+1));
                synchronized(workers) {
                    workers[i] = new Worker();
                    svc.execute(workers[i]);
                }
            }
         }
    }

}
