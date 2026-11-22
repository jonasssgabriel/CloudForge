package com.mycompany.servidorcloudforge;

/**
 * Demonstra a condição de corrida: 8 threads x 100000 incrementos.
 * Sem synchronized, "contador++" (ler, somar, gravar) perde atualizações.
 */
public class CorridaDemo {

    static int semLock = 0;
    static int comLock = 0;
    static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        final int THREADS = 8, INCREMENTOS = 100_000;
        Thread[] a = new Thread[THREADS];
        Thread[] b = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            a[i] = new Thread(() -> { for (int j = 0; j < INCREMENTOS; j++) semLock++; });
            b[i] = new Thread(() -> {
                for (int j = 0; j < INCREMENTOS; j++) {
                    synchronized (lock) { comLock++; }
                }
            });
        }
        for (Thread t : a) t.start();
        for (Thread t : a) t.join();
        for (Thread t : b) t.start();
        for (Thread t : b) t.join();
        System.out.println("Esperado: " + (THREADS * INCREMENTOS));
        System.out.println("SEM synchronized: " + semLock);
        System.out.println("COM synchronized: " + comLock);
    }
}
