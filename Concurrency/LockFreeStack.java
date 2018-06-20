package com.examplle.helloword;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;


/*
* 实现了一个无锁的stack,并且进行了一些实验
*
* 解释一下运行的结果
* 当线程数越多的时候,碰撞的概率越高,但是这里的操作几乎是纯内存操作
* 线程的数目超过cpu的数量不应该能增加性能
* 仔细分析,当一个线程运行到get操作之后切换,另一个线程又运行到get之后又切换,然后第三个线程运行导致前两个线程本次cas失败
* 所以在这种竞争程度非常高的case下,单个的线程效率反而是最高的
*
*  采取一个优化,当cas失败次数达到一定次数之后,采取 Thread.yield() / parkNaos等方式等待一下,可以有效降低冲突概率
*  为什么可以? 回到上面的case,由于线程A get失败之后就等了一下,然后这时候线程B也等了一下,线程c成功,下一次线程A先醒来
*  直接cas成功, 这里可以降低线程的上下文切换和竞争的激烈程度
*
*  最后一个问题,即便是没有冲突几次,但是效率还是和单线程差不多?
*  这是因为除开线程上下文切换之外,每个线程都竞争同一个对象,这会引起读写屏障/缓存锁/总线流量增大等问题
*  在这个层面上,还是只可能串行执行. 但是如果加上IO／等待这样的操作,多线程明显快很多
*
*  和synchronized的比较,发现synchronized效率稍微高一点(说明优化的很高效了)
* */


public class HelloWord {
    public static void main(String[] args) {

        final long startTime = System.currentTimeMillis();
        run();
        final long duration = System.currentTimeMillis() - startTime;
        System.out.println("duration: " + duration);
    }

    public static void run(){
        final long outer = 100;
        final long inner = 300000;
        ExecutorService exec = Executors.newFixedThreadPool(4);
        LockFreeStack<Integer> s = new LockFreeStack1<Integer>();
        for(int i = 0; i < outer; ++i){
            if( i % 2 == 0){
                final int j = i;
                exec.submit(() -> {
                    for (int k = 0; k < inner; k++) {
                        s.push(j << 2);
                    }
                });
            }else{
                exec.submit(()->{
                    for (int k = 0; k < inner; k++) {
                        int ret = s.pop();
                        if(ret == 100){
                           System.out.println(ret);
                        }
                    }
                });
            }
        }

        try {
            exec.shutdown();
            exec.awaitTermination(20,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("interrupted exception occur");
        }
        int collisionNum = ((LockFreeStack1<Integer>) s).collisionNum;
        System.out.println("CollisionNum: " + collisionNum + " percentage : %" + collisionNum / (outer * inner / 100 + 0.0));
    }

}

/*
* 实现一个无锁的并发stack
* */

interface LockFreeStack<V>{
    void push(V v);
    V pop();
    void handleCollision();

}

class LockFreeStack1<V> implements LockFreeStack<V>{
    private class Node{
        Node next;
        V val;

        Node(V v){
            val = v;
            next = null;
        }
    }

    AtomicReference<Node> top;
    int collisionNum = 0;// 冲突的次数,代表并发程度的高低

    LockFreeStack1(){
        top = new AtomicReference<Node>(null);
    }

    public void push(V value){
        Node node = new Node(value);
        while(!top.compareAndSet(node.next = top.get(),node)){
            collisionNum += 1;
            handleCollision();
        }
    }

    public V pop(){
        Node oldTop;
        while ((oldTop = top.get()) != null && !top.compareAndSet(oldTop,oldTop.next)){
            collisionNum += 1;
            handleCollision();
        }
        return oldTop == null ? null : oldTop.val;
    }

    /*
    * 当冲突的情况比较严重的时候,需要执行的一些操作
    * 可以让线程适当的park一下
    * */
    public void handleCollision(){
        if(collisionNum < 100){
            Thread.yield();
        }else{
            //Thread.yield();
            LockSupport.parkNanos(1000_000_0);// 10ms
        }
    }
}

class LockFreeStack2<V> implements LockFreeStack<V>{
    private class Node{
        Node next;
        V val;

        Node(V v){
            val = v;
            next = null;
        }
    }

    Node top;
    int collisionNum = 0;// 冲突的次数,代表并发程度的高低

    LockFreeStack2(){
        top = null;
    }

    public void push(V value){
        Node node = new Node(value);
        synchronized (this){
            node.next = top;
            top = node;
        }
    }

    public V pop(){
        synchronized (this){
            if(top == null) {
                return null;
            }

            Node oldTop = top;
            top = top.next;
            return oldTop.val;
        }
    }

    /*
     * 当冲突的情况比较严重的时候,需要执行的一些操作
     * 可以让线程适当的park一下
     * */
    public void handleCollision(){
        return;
    }


}
