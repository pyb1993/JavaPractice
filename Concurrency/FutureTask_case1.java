package com.examplle.helloword;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.*;


/*
 假设有一个非常耗时/不允许重复运行 的操作(创建一个链接,分解一个很大素数 ),这些操作的结果可以被一个key缓存
 现在在多线程的程序下,应该如何保证 这些操作的效率足够高,且不会重复执行
 
 答案是使用ConcurrentHashMap和FutureTask
 首先考虑为什么要这样做,在多线程环境环境下,使用ConcurrentHashMap是很正常的,但是为什么不直接缓存结果呢
 如果缓存结果,考虑最核心的并发情况:
 ret = cache.get(key)
 if(ret == null){
 // 这里只能直接加锁
 }
 上面的程序只能手动进行加锁(double check),否则完全可能出现两次操作
 但是,使用ConcurrentHashMap并没有暴露内部的分段锁,这样就只能对整个对象进行加锁
 并发性能会受到很大的影响,也不是没有办法,比如这里可以使用一个新的加锁的办法,那就是
 自己对key进行分片,然后对片加锁。但是这相当于把ConcurrentHashMap的工作又做了一遍,
 而且还进行了双重加锁。不管是工作量,维护难度,还是性能都不占优势。
 
 有没有可能不显示的加锁呢,实际上我们可以利用putIfAbsent这个操作来执行,这个操作实际上
 就隐藏了分段加锁的操作,问题在于结果。如果我们要先计算结果,再调用这个api,就又回到了
 原来的困境,对于「计算结果」部分代码的并发造成重复计算,所以只有先放入一个东西进行占位,
 然后才利用putIfAbsent返回的结果判断当前map是否存在了缓存,如果已经存在了,那么就不要重复
 计算了。
 
 现在问题来了,如果我们随便用一个东西x进行占位,那么别的线程就只能获取到x,它只知道x现在已经有线程
 占了,但是结果啥啥时候出来?这里就只能不断循环等待了。检查直到结果已经计算出来了再返回。
 这里可以用ConditionVariable作为占位的东西,然后其他线程阻塞在这里,直到第一个线程计算完成之后,把真
 的结果放进去。。。当然这样具体实现就会非常麻烦。
 
 这时候再来看FutureTask就非常顺眼了,因为它有这样几个特点,恰好解决了我们上面说的所有问题:
 1 第一个,它代表了一种计算过程,可以直接调用get操作获取结果,如果还没有计算完就阻塞。这就解决了
 「啥时候可以拿到真正的结果」这个问题
 2 它确保只调用一次(run方法里面有对状态的检查,同时有CAS操作确保不会有两个同时执行)
 3 它不需要外部加锁,直接利用ConCurrentHashMap的锁就ok了
 */



public class HelloWord {
    public static void main(String[] args) {
        int threadNum = 10;
        final long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(threadNum);
        Memoizer2<String,BigInteger> cache = new Memoizer2<>(new ExpensiveCompute());
        
        // 执行任务,每个任务计算10万次
        for (int i = 0; i < threadNum; i++) {
            Worker w = new Worker(cache,latch);
            Thread t = new Thread(w);
            t.start();
        }
        
        waitWorkersDone(latch);
        final long duration = System.currentTimeMillis() - startTime;
        System.out.println(duration);
    }
    
    private static void waitWorkersDone(CountDownLatch latch){
        // 等待其他线程结束
        try {
            //调用await方法阻塞当前线程，等待子线程完成后在继续执行
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}


interface Computable<A,V> {
    V compute(A arg) throws InterruptedException;
}

class ExpensiveCompute implements Computable<String, BigInteger>{
    public BigInteger compute(String arg){
        try{
            //模拟非常耗时的操作
            System.out.printf("***** thread %d , compute %s\n",Thread.currentThread().getId(),arg);
            Thread.sleep(1000);
        }catch (Exception  e){
            throw new RuntimeException(e);
        }
        return new BigInteger(arg);
    }
}

/*
 * 一个用来保存缓存的类
 * 用Future(注意不是FutureTask,方便扩展)作为缓存的结果,目的是避免重复计算(如果直接存储计算结果,需要做到对具体一个缓存加锁,而不是对整个map加锁,比较麻烦)
 * 用ConCurrentHashMap作为缓存的结构,做到线程安全
 *
 * */

interface Memoizer<A,V>{
    V compute(A arg);
    
}

class Memoizer1<A, V> implements  Memoizer<A,V>{
    private final ConcurrentHashMap<A,Future<V>> cache = new ConcurrentHashMap<>();
    private final Computable<A,V> c;
    
    public Memoizer1(Computable<A,V> c){
        this.c = c;
    }
    
    /*计算结果,如果有缓存就直接获取结果并且等待
     * 否则直接计算,并且将结果保存在缓存里面
     * */
    public V compute(A arg){
        Future<V> f = cache.get(arg);
        Callable<V> eval = new Callable<V>(){
            public V call() throws InterruptedException {
                return c.compute(arg);
            }
        };
        if(f == null){
            FutureTask<V> ft = new FutureTask<>(eval);
            f = cache.putIfAbsent(arg,ft);
            /* 注意这里有一个corner case,由于 ft是new出来的,所以如果两个线程同时访问,
             *  会导致ft被执行多次,所以应该先将f放入ConcurrentHashMap里面,通过原子操作判断是否需要执行
             * */
            if(f == null){
                f = ft;
                ft.run();// ft开始运行
            }
        }
        try{
            return f.get();
        }catch (Exception e){
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

/* 第二个类型的Memoizer,还是使用ConcurrentHashMap,但是把结果存储起来,而不是存储计算过程
 * 利用这个可以验证,确实有不少重复计算的情况
 * */
class Memoizer2<A, V> implements  Memoizer<A,V>{
    private final ConcurrentHashMap<A,V> cache = new ConcurrentHashMap<>();
    private final Computable<A,V> c;
    
    public Memoizer2(Computable<A,V> c){
        this.c = c;
    }
    
    /*计算结果,如果有缓存就直接获取结果并且等待
     * 否则直接计算,并且将结果保存在缓存里面
     * 相对第一种做法,这里没有考虑两者重复计算的可能
     * */
    public V compute(A arg){
        V ret = cache.get(arg);
        
        if(ret == null){
            // 表示没有命中,需要来计算结果
            try {
                ret = c.compute(arg);
                cache.putIfAbsent(arg,ret);
            }catch (Exception e) {
                System.out.println(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return ret;
    }
}



/* 用来执行一个任务,该任务可以决定使用外部传递进来的缓存cache
 *  每个线程执行1到多个任务,所有任务都使用同一个cache
 * */
class Worker implements Runnable {
    /*内部类,用于模拟一个非常耗时的操作*/
    private final Memoizer<String,BigInteger> cache;
    private final CountDownLatch latch;
    
    public Worker(Memoizer<String,BigInteger> c,CountDownLatch latch){
        this.cache = c;
        this.latch = latch;
    }
    
    @Override
    public void run() {
        /*随机生成1到100000里面的随机数,并且执行对应的计算*/
        System.out.printf("Thread:%d start\n",Thread.currentThread().getId());
        for (int i = 0; i < 10; i++) {
            Random r = new Random();
            Integer a = r.nextInt(5);
            cache.compute(a.toString()).toString();
        }
        latch.countDown();
    }
}
