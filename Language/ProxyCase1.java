package com.examplle.helloword;

/* 动态代理模式:
 *  动态代理对比静态代理的优势在于: 可以无视需要「代理」的对象的具体类型和方法,直接在invoke上面添加代码
 *  但是也有不适用的地方,比如现在要对一个类(final无法继承)的多个方法都执行包装,且执行的代码不一样,那么如果使用proxy
 *  就需要在一个invoke方法里面判断各种不同的方法,然后分别执行
 *  当然可以使用状态模式(下面有一个例子),但是使用静态代理,就可以很简单的解决这个问题,对需要的方法直接wrap就好
 *
 *
 *  如果是对N个类的M个方法进行wrap,那么使用静态代理不划算,因为类还有可能继续增加
 *  那么这个时候动态代理配合状态模式,就会相对简单一点。只需要实现M个状态,然后针对不同的方法生成不同的SubHandler就好
 *
 *  如果使用静态代理,那么首先要写N个静态代理类,然后针对不同的方法执行代理操作,这样需要 M * N部分的逻辑重复
 *  当然可以想办法把相同的逻辑抽象出来,然后统一调用,但是还是会多很多胶水代码
 *
 *  另外一个区别是,如果只有定义没有实现(远程调用的时候),那么没有办法直接生成对象,这个时候动态代理就比较有效,因为
 *  不需要具体的对象,直接在invoke里面实现具体的通信协议就好,所需要的无非是远程通信对象的参数
 *  静态代理就不好搞了,这个时候没有办法生成对应的对象(除非自己实现这个接口),但是如果现在有100个远程调用的对象
 *  那不是要实现100个代理类吗
 *
 * */


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class HelloWord {
    public static void main(String[] args) {
        final long startTime = System.currentTimeMillis();
        run();
        final long duration = System.currentTimeMillis() - startTime;
        System.out.println("duration: " + duration);
    }
    
    public static void run(){
        Integer[] A = new Integer[] {1,2,3,4,5,56,6,6,9,11,17,20,18};
        
        Object[] B = new Object[A.length];
        int i = 0;
        for (Integer a : A){
            B[i++] = new ProxyHandler().bind(a);
        }
        
        Arrays.binarySearch(B,20);
        System.out.println(B[0].toString());
        //Arrays.sort(B);
    }
    
}

class ProxyHandler implements InvocationHandler {
    private Object target;
    
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable{
        class SubHandlerDefault{
            Object invoke( ) throws Throwable{
                return m.invoke(target,args);
            };
        }
        
        class SubHandlerOfCompareTo extends SubHandlerDefault{
            public Object invoke() throws Throwable {
                assert(args.length == 1);
                return m.invoke(target,args);
            }
        }
        
        class SubHandlerOfToString extends SubHandlerDefault{
            public Object invoke() throws Throwable {
                assert(args.length == 0);
                return (String)m.invoke(target,args) + ": called by subhandler";
            }
        }
        
        Callable<SubHandlerDefault> getSubHandler = () -> {
            if(m.getName().equals("compareTo")){
                return new SubHandlerOfCompareTo();
            }else if (m.getName().equals("toString")){
                return new SubHandlerOfToString();
            }else{
                return new SubHandlerDefault();
            }
        };
        
        Object result = null;
        showParameters(m,args);
        SubHandlerDefault subHandler = getSubHandler.call();// 根据传入的方法m来获取指定的invoke
        result = subHandler.invoke();
        return result;
    }
    
    public Proxy bind(Object tar){
        this.target = tar;
        return (Proxy) Proxy.newProxyInstance(null,target.getClass().getInterfaces(),this);
    }
    
    
    private void showParameters(Method m,Object[] args){
        System.out.print(target);
        System.out.print("." + m.getName() + "(");
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                System.out.print(args[i]);
                if (i < args.length - 1) {
                    System.out.println(", ");
                }
            }
        }
        System.out.println(")");
    }
}

