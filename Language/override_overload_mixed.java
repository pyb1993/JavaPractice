
class parent {
    public void compute2(double a){
        System.out.println("parent double");
    };
}

class child extends parent{
    public void compute2(long a){
        System.out.println("child long");
    };
}


public class HelloWord {
    public static void main(String[] args) {
        parent c = new child();// 编译类型是 ttt,运行时类型是ttt2
        long s = 1000L;
        c.compute2(s);// 输出什 parent double! JDK10的环境
        /*
        * 一个猜测为什么会得到这样的结果
        * 很明显是编译器在编译器就做出来静态分配的结果
        * 考虑一下如果compute2是final方法,得到这样的结果是很显然的
        * 为什么?因为final方法没有办法被重写,它不会在运行期产生多态，所以我们认为这个方法就是parent里面的compute
        * 回到这个问题上,由于child没有对这个方法compute(double)进行重写,所以编译器认为这个方法不会在运行期产生多态,所以静态分配
        * 意义: 不要混用重写和重载,非常容易搞糊涂和难以维护,但是真遇到问题了应该有能力弄明白原因～
        * */

    }
}